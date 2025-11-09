package dev.jkiakumbo.paymentorchestrator.processorservice.service
import com.fasterxml.jackson.databind.ObjectMapper
import dev.jkiakumbo.paymentorchestrator.processorservice.domain.PaymentTransaction
import dev.jkiakumbo.paymentorchestrator.processorservice.domain.PaymentTransactionRepository
import dev.jkiakumbo.paymentorchestrator.processorservice.domain.TransactionStatus
import dev.jkiakumbo.paymentorchestrator.processorservice.events.CompensationCompletedEvent
import dev.jkiakumbo.paymentorchestrator.processorservice.events.CompensationRequestedEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class CompensationService(
    private val transactionRepository: PaymentTransactionRepository,
    private val objectMapper: ObjectMapper,
    private val kafkaTemplate: KafkaTemplate<String, String>
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun handleCompensation(event: CompensationRequestedEvent) {
        logger.info("Processing compensation for payment: ${event.paymentId}, reason: ${event.reason}")

        val transaction = transactionRepository.findByPaymentId(event.paymentId)
        if (transaction == null) {
            logger.warn("No transaction found for compensation: ${event.paymentId}")
            publishCompensationCompleted(event.paymentId)
            return
        }

        try {
            when (transaction.status) {
                TransactionStatus.COMPLETED -> {
                    // Reverse the transaction with processor
                    reverseTransactionWithProcessor(transaction)
                    transaction.status = TransactionStatus.CANCELLED
                    transaction.updatedAt = LocalDateTime.now()
                    transactionRepository.save(transaction)
                    logger.info("Successfully reversed transaction: ${transaction.paymentId}")
                }
                TransactionStatus.PROCESSING -> {
                    // Mark as cancelled - processor may still complete, but we'll handle idempotently
                    transaction.status = TransactionStatus.CANCELLED
                    transaction.updatedAt = LocalDateTime.now()
                    transactionRepository.save(transaction)
                    logger.info("Marked processing transaction as cancelled: ${transaction.paymentId}")
                }
                else -> {
                    logger.info("Transaction ${transaction.paymentId} in state ${transaction.status} requires no compensation")
                }
            }

            publishCompensationCompleted(event.paymentId)
        } catch (e: Exception) {
            logger.error("Compensation failed for payment: ${event.paymentId}", e)
            // In production, you might want to retry compensation or send to DLQ
        }
    }

    private fun reverseTransactionWithProcessor(transaction: PaymentTransaction) {
        logger.info("Reversing transaction with processor: ${transaction.externalTransactionId}")

        // Mock processor reversal call
        Thread.sleep(150)

        // In real implementation, this would call processor's reversal/refund API
        // For now, we'll just simulate a successful reversal
        logger.info("Successfully reversed transaction with processor: ${transaction.externalTransactionId}")
    }

    private fun publishCompensationCompleted(paymentId: java.util.UUID) {
        val event = CompensationCompletedEvent(paymentId = paymentId)

        kafkaTemplate.send(
            MessageBuilder.withPayload(objectMapper.writeValueAsString(event))
                .setHeader(KafkaHeaders.TOPIC, "")
                .setHeader("paymentId", paymentId.toString())
                .setHeader("eventType", "CompensationCompletedEvent")
                .setHeader("correlationId", java.util.UUID.randomUUID().toString())
                .build()
        )

        logger.info("Compensation completed for payment: $paymentId")
    }
}