package dev.jkiakumbo.paymentorchestrator.processorservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import dev.jkiakumbo.paymentorchestrator.processorservice.domain.PaymentTransaction
import dev.jkiakumbo.paymentorchestrator.processorservice.domain.PaymentTransactionRepository
import dev.jkiakumbo.paymentorchestrator.processorservice.domain.TransactionStatus
import dev.jkiakumbo.paymentorchestrator.processorservice.events.PaymentExecutedEvent
import dev.jkiakumbo.paymentorchestrator.processorservice.events.PaymentExecutionFailedEvent
import dev.jkiakumbo.paymentorchestrator.processorservice.events.PaymentExecutionRequestedEvent
import dev.jkiakumbo.paymentorchestrator.processorservice.events.PaymentExecutionRetryEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.support.MessageBuilder
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
class PaymentProcessorService(
    private val transactionRepository: PaymentTransactionRepository,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    private val compensationService: CompensationService
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun processPayment(event: PaymentExecutionRequestedEvent) {
        logger.info("Processing payment execution for: ${event.paymentId}")

        // Check idempotency - if we already processed this payment
        val existingTransaction = transactionRepository.findByPaymentId(event.paymentId)
        if (existingTransaction != null) {
            handleExistingTransaction(existingTransaction, event)
            return
        }

        // Create new transaction record
        val transaction = PaymentTransaction(
            paymentId = event.paymentId,
            amount = event.amount,
            currency = event.currency,
            merchantId = event.merchantId,
            customerId = event.customerId,
            status = TransactionStatus.PENDING
        )

        transactionRepository.save(transaction)

        // Process the payment
        processPaymentExecution(transaction)
    }

    private fun handleExistingTransaction(transaction: PaymentTransaction, event: PaymentExecutionRequestedEvent) {
        logger.info("Payment transaction already exists for: ${event.paymentId}, status: ${transaction.status}")

        when (transaction.status) {
            TransactionStatus.COMPLETED -> {
                // Idempotency - already succeeded, publish success event
                publishSuccessEvent(transaction)
            }
            TransactionStatus.FAILED -> {
                if (transaction.canRetry()) {
                    logger.info("Retrying failed payment: ${event.paymentId}, retry count: ${transaction.retryCount}")
                    transaction.incrementRetry()
                    transaction.markAsProcessing()
                    transactionRepository.save(transaction)

                    // Publish retry event for observability
                    publishRetryEvent(transaction, "Retrying failed payment")

                    // Retry processing
                    processPaymentExecution(transaction)
                } else {
                    logger.warn("Payment ${event.paymentId} has exceeded maximum retry attempts")
                    // Do nothing - already failed and cannot retry
                }
            }
            TransactionStatus.PROCESSING -> {
                logger.info("Payment ${event.paymentId} is already being processed")
                // Do nothing - already processing
            }
            else -> {
                // For other statuses, retry processing
                processPaymentExecution(transaction)
            }
        }
    }

    @Retryable(
        retryFor = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 2000, multiplier = 2.0)
    )
    private fun processPaymentExecution(transaction: PaymentTransaction) {
        try {
            transaction.markAsProcessing()
            transactionRepository.save(transaction)

            logger.info("Executing payment with processor for: ${transaction.paymentId}")

            // Mock payment processor integration
            val processorResult = mockPaymentProcessorCall(transaction)

            if (processorResult.success) {
                transaction.markAsCompleted(
                    transactionId = processorResult.transactionId!!,
                    processorRef = processorResult.processorReference
                )
                transactionRepository.save(transaction)

                publishSuccessEvent(transaction)
                logger.info("Payment processed successfully: ${transaction.paymentId}")
            } else {
                throw RuntimeException("Payment processor declined transaction: ${processorResult.declineReason}")
            }
        } catch (e: Exception) {
            logger.error("Payment processing failed for: ${transaction.paymentId}", e)

            transaction.markAsFailed(
                reason = e.message ?: "Unknown processor error",
                processorResponse = e.localizedMessage
            )
            transactionRepository.save(transaction)

            val canRetry = e !is PermanentProcessorException && transaction.canRetry()

            publishFailureEvent(transaction, e.message ?: "Unknown error", canRetry)
        }
    }

    private fun mockPaymentProcessorCall(transaction: PaymentTransaction): ProcessorResult {
        // Simulate processing delay
        Thread.sleep((100 + Math.random() * 400).toLong())

        // Mock processor logic with realistic scenarios
        return when {
            // High amount decline
            transaction.amount > java.math.BigDecimal("50000") -> {
                ProcessorResult(
                    success = false,
                    declineReason = "Transaction amount exceeds limit"
                )
            }
            // Specific merchant blacklist
            transaction.merchantId.contains("BLACKLIST") -> {
                ProcessorResult(
                    success = false,
                    declineReason = "Merchant not authorized"
                )
            }
            // Specific customer issues
            transaction.customerId.startsWith("RISKY") -> {
                ProcessorResult(
                    success = false,
                    declineReason = "Customer risk level too high"
                )
            }
            // Network timeout simulation (5% chance)
            Math.random() < 0.05 -> {
                throw ProcessorTimeoutException("Processor network timeout")
            }
            // Processor system error simulation (3% chance)
            Math.random() < 0.03 -> {
                throw ProcessorSystemException("Processor system temporarily unavailable")
            }
            // General decline (2% chance)
            Math.random() < 0.02 -> {
                ProcessorResult(
                    success = false,
                    declineReason = "Insufficient funds"
                )
            }
            // Success case
            else -> {
                ProcessorResult(
                    success = true,
                    transactionId = "PTX_${System.currentTimeMillis()}_${transaction.paymentId.toString().take(8)}",
                    processorReference = "REF_${UUID.randomUUID().toString().take(8)}"
                )
            }
        }
    }

    private fun publishSuccessEvent(transaction: PaymentTransaction) {
        val event = PaymentExecutedEvent(
            paymentId = transaction.paymentId,
            transactionId = transaction.externalTransactionId!!,
            processorReference = transaction.processorReference
        )

        kafkaTemplate.send(
            MessageBuilder.withPayload(objectMapper.writeValueAsString(event))
                .setHeader(KafkaHeaders.TOPIC, "payment-execution-results")
                .setHeader("paymentId", transaction.paymentId.toString())
                .setHeader("eventType", "PaymentExecutedEvent")
                .setHeader("correlationId", UUID.randomUUID().toString())
                .build()
        )
    }

    private fun publishFailureEvent(transaction: PaymentTransaction, reason: String, canRetry: Boolean) {
        val event = PaymentExecutionFailedEvent(
            paymentId = transaction.paymentId,
            reason = reason,
            canRetry = canRetry
        )

        kafkaTemplate.send(
            MessageBuilder.withPayload(objectMapper.writeValueAsString(event))
                .setHeader(KafkaHeaders.TOPIC, "payment-execution-results")
                .setHeader("paymentId", transaction.paymentId.toString())
                .setHeader("eventType", "PaymentExecutionFailedEvent")
                .setHeader("correlationId", UUID.randomUUID().toString())
                .build()
        )
    }

    private fun publishRetryEvent(transaction: PaymentTransaction, reason: String) {
        val event = PaymentExecutionRetryEvent(
            paymentId = transaction.paymentId,
            retryCount = transaction.retryCount,
            reason = reason
        )

        kafkaTemplate.send(
            MessageBuilder.withPayload(objectMapper.writeValueAsString(event))
                .setHeader(KafkaHeaders.TOPIC, "processor-service-dlq")
                .setHeader("paymentId", transaction.paymentId.toString())
                .setHeader("eventType", "PaymentExecutionRetryEvent")
                .setHeader("correlationId", UUID.randomUUID().toString())
                .build()
        )
    }

    @Transactional
    fun findStuckTransactions(): List<PaymentTransaction> {
        val cutoff = LocalDateTime.now().minusMinutes(10)
        return transactionRepository.findByStatusAndCreatedAtBefore(
            TransactionStatus.PROCESSING,
            cutoff
        )
    }

    @Transactional
    fun retryStuckTransaction(transactionId: UUID) {
        val transaction = transactionRepository.findById(transactionId).orElseThrow {
            IllegalArgumentException("Transaction not found: $transactionId")
        }

        if (transaction.status == TransactionStatus.PROCESSING) {
            logger.warn("Retrying stuck transaction: $transactionId")
            transaction.markAsFailed("Stuck transaction - manual retry", "MANUAL_RETRY")
            transactionRepository.save(transaction)

            // This will trigger a retry in the next payment execution request
        }
    }

    data class ProcessorResult(
        val success: Boolean,
        val transactionId: String? = null,
        val processorReference: String? = null,
        val declineReason: String? = null
    )
}

// Custom exceptions for different processor error scenarios
class ProcessorTimeoutException(message: String) : Exception(message)
class ProcessorSystemException(message: String) : Exception(message)
class PermanentProcessorException(message: String) : Exception(message)