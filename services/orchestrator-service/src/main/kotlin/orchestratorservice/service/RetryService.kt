package dev.jkiakumbo.paymentorchestrator.orchestratorservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.domain.Payment
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.domain.PaymentRepository
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.domain.PaymentState
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.events.FundsReservationRequestedEvent
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.events.LedgerUpdateRequestedEvent
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.events.PaymentExecutionRequestedEvent
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.state.PaymentEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.support.MessageBuilder
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
class RetryService(
    private val paymentRepository: PaymentRepository,
    private val paymentStateMachineService: PaymentStateMachineService,
    private val objectMapper: ObjectMapper,
    private val kafkaTemplate: KafkaTemplate<String, String>
) {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val retryQueue = mutableMapOf<UUID, RetryAttempt>()

    data class RetryAttempt(
        val paymentId: UUID,
        val step: String,
        val reason: String,
        val scheduledAt: LocalDateTime,
        val retryCount: Int
    )

    fun scheduleRetry(paymentId: UUID, step: String, reason: String) {
        val payment = paymentRepository.findById(paymentId).orElseThrow {
            IllegalArgumentException("Payment not found: $paymentId")
        }

        payment.incrementRetry()
        paymentRepository.save(payment)

        val retryAttempt = RetryAttempt(
            paymentId = paymentId,
            step = step,
            reason = reason,
            scheduledAt = LocalDateTime.now().plusSeconds(calculateBackoff(payment.retryCount)),
            retryCount = payment.retryCount
        )

        retryQueue[paymentId] = retryAttempt
        logger.info("Scheduled retry for payment: $paymentId, step: $step, attempt: ${payment.retryCount}")
    }

    @Scheduled(fixedRate = 10000) // Check every 10 seconds
    @Transactional
    fun processRetries() {
        val now = LocalDateTime.now()
        val dueRetries = retryQueue.values.filter { it.scheduledAt.isBefore(now) }

        for (retry in dueRetries) {
            try {
                executeRetry(retry)
                retryQueue.remove(retry.paymentId)
            } catch (e: Exception) {
                logger.error("Failed to execute retry for payment: ${retry.paymentId}", e)
            }
        }
    }

    private fun executeRetry(retry: RetryAttempt) {
        val payment = paymentRepository.findById(retry.paymentId).orElseThrow {
            IllegalArgumentException("Payment not found: ${retry.paymentId}")
        }

        logger.info("Executing retry for payment: ${payment.id}, step: ${retry.step}, attempt: ${retry.retryCount}")

        when (retry.step) {
            "FUNDS_RESERVATION" -> {
                paymentStateMachineService.sendEvent(payment.id, PaymentEvent.MANUAL_RETRY)
                payment.state = PaymentState.FUNDS_RESERVATION_PENDING
                payment.markStep("FUNDS_RESERVATION")

                val fundsEvent = FundsReservationRequestedEvent(
                    paymentId = payment.id,
                    amount = payment.amount,
                    currency = payment.currency,
                    customerId = payment.customerId
                )

                kafkaTemplate.send(
                    MessageBuilder.withPayload(objectMapper.writeValueAsString(fundsEvent))
                        .setHeader(KafkaHeaders.TOPIC, "payment-execution-requests")
                        .setHeader("paymentId", payment.id.toString())
                        .setHeader("correlationId", payment.correlationId)
                        .setHeader("traceId", payment.traceId)
                        .setHeader("eventType", "FundsReservationRequestedEvent")
                        .setHeader("isRetry", "true")
                        .setHeader("retryCount", payment.retryCount.toString())
                        .build()
                )
            }
            "PAYMENT_EXECUTION" -> {
                paymentStateMachineService.sendEvent(payment.id, PaymentEvent.MANUAL_RETRY)
                payment.state = PaymentState.PROCESSOR_EXECUTION_PENDING
                payment.markStep("PAYMENT_EXECUTION")

                val processorEvent = PaymentExecutionRequestedEvent(
                    paymentId = payment.id,
                    amount = payment.amount,
                    currency = payment.currency,
                    merchantId = payment.merchantId,
                    customerId = payment.customerId
                )

                kafkaTemplate.send(
                    MessageBuilder.withPayload(objectMapper.writeValueAsString(processorEvent))
                        .setHeader("paymentId", payment.id.toString())
                        .setHeader("correlationId", payment.correlationId)
                        .setHeader("traceId", payment.traceId)
                        .setHeader("eventType", "PaymentExecutionRequestedEvent")
                        .setHeader("isRetry", "true")
                        .setHeader("retryCount", payment.retryCount.toString())
                        .build()
                )
            }
            "LEDGER_UPDATE" -> {
                paymentStateMachineService.sendEvent(payment.id, PaymentEvent.MANUAL_RETRY)
                payment.state = PaymentState.LEDGER_UPDATE_PENDING
                payment.markStep("LEDGER_UPDATE")

                val ledgerEvent = LedgerUpdateRequestedEvent(
                    paymentId = payment.id,
                    amount = payment.amount,
                    currency = payment.currency,
                    merchantId = payment.merchantId,
                    transactionId = payment.transactionId!!
                )

                kafkaTemplate.send(
                    MessageBuilder.withPayload(objectMapper.writeValueAsString(ledgerEvent))
                        .setHeader("paymentId", payment.id.toString())
                        .setHeader("correlationId", payment.correlationId)
                        .setHeader("traceId", payment.traceId)
                        .setHeader("eventType", "LedgerUpdateRequestedEvent")
                        .setHeader("isRetry", "true")
                        .setHeader("retryCount", payment.retryCount.toString())
                        .build()
                )
            }
        }

        paymentRepository.save(payment)
        logger.info("Retry executed for payment: ${payment.id}")
    }

    private fun calculateBackoff(retryCount: Int): Long {
        // Exponential backoff: 5, 10, 20, 40 seconds...
        return (5 * Math.pow(2.0, (retryCount - 1).toDouble())).toLong()
    }

    @Transactional
    fun findStuckPayments(): List<Payment> {
        val cutoff = LocalDateTime.now().minusMinutes(10)
        val stuckStates = listOf(
            PaymentState.FRAUD_CHECK_PENDING,
            PaymentState.FUNDS_RESERVATION_PENDING,
            PaymentState.PROCESSOR_EXECUTION_PENDING,
            PaymentState.LEDGER_UPDATE_PENDING
        )

        return paymentRepository.findStuckPayments(stuckStates, cutoff)
    }

    @Transactional
    fun manualRetry(paymentId: UUID) {
        val payment = paymentRepository.findById(paymentId).orElseThrow {
            IllegalArgumentException("Payment not found: $paymentId")
        }

        when (payment.state) {
            PaymentState.FRAUD_CHECK_FAILED -> {
                scheduleRetry(paymentId, "FRAUD_CHECK", "Manual retry")
            }
            PaymentState.FUNDS_RESERVATION_FAILED -> {
                scheduleRetry(paymentId, "FUNDS_RESERVATION", "Manual retry")
            }
            PaymentState.PROCESSOR_EXECUTION_FAILED -> {
                scheduleRetry(paymentId, "PAYMENT_EXECUTION", "Manual retry")
            }
            PaymentState.LEDGER_UPDATE_FAILED -> {
                scheduleRetry(paymentId, "LEDGER_UPDATE", "Manual retry")
            }
            else -> {
                throw IllegalArgumentException("Payment ${payment.id} in state ${payment.state} cannot be manually retried")
            }
        }
    }
}