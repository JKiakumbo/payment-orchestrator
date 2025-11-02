package dev.jkiakumbo.paymentorchestrator.orchestratorservice.service

import dev.jkiakumbo.paymentorchestrator.orchestratorservice.domain.Payment
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.domain.PaymentRepository
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.domain.PaymentState
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.events.CompensationCompletedEvent
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.events.FraudCheckCompletedEvent
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.events.FraudCheckRequestedEvent
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.events.FundsReservationFailedEvent
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.events.FundsReservationRequestedEvent
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.events.FundsReservedEvent
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.events.LedgerUpdateFailedEvent
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.events.LedgerUpdateRequestedEvent
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.events.LedgerUpdatedEvent
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.events.PaymentCompensatedEvent
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.events.PaymentCompletedEvent
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.events.PaymentExecutedEvent
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.events.PaymentExecutionFailedEvent
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.events.PaymentExecutionRequestedEvent
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.events.PaymentFailedEvent
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.state.PaymentEvent
import io.micrometer.tracing.Tracer
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Service
class OrchestrationService(
    private val paymentRepository: PaymentRepository,
    private val paymentStateMachineService: PaymentStateMachineService,
    private val compensationService: CompensationService,
    private val retryService: RetryService,
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val tracer: Tracer
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun initiatePayment(
        amount: BigDecimal,
        currency: String,
        merchantId: String,
        customerId: String
    ): UUID {
        val paymentId = UUID.randomUUID()
        val correlationId = UUID.randomUUID().toString()
        val traceId = tracer.currentSpan()?.context()?.traceId() ?: "no-trace"

        logger.info("Initiating payment: $paymentId, amount: $amount, merchant: $merchantId, customer: $customerId")

        // Create payment record
        val payment = Payment(
            id = paymentId,
            amount = amount,
            currency = currency,
            merchantId = merchantId,
            customerId = customerId,
            state = PaymentState.INITIATED,
            correlationId = correlationId,
            traceId = traceId
        )

        paymentRepository.save(payment)

        // Start state machine
        paymentStateMachineService.startStateMachine(paymentId)
        paymentStateMachineService.sendEvent(paymentId, PaymentEvent.FRAUD_CHECK_REQUESTED)

        // Update payment state
        payment.state = PaymentState.FRAUD_CHECK_PENDING
        payment.markStep("FRAUD_CHECK")
        paymentRepository.save(payment)

        // Publish fraud check request
        val fraudEvent = FraudCheckRequestedEvent(
            paymentId = paymentId,
            amount = amount,
            currency = currency,
            merchantId = merchantId,
            customerId = customerId
        )

        kafkaTemplate.send(
            MessageBuilder.withPayload(fraudEvent)
                .setHeader("paymentId", paymentId.toString())
                .setHeader("correlationId", correlationId)
                .setHeader("traceId", traceId)
                .setHeader("eventType", "FraudCheckRequestedEvent")
                .build()
        )

        logger.info("Payment initiated successfully: $paymentId")
        return paymentId
    }

    @Transactional
    fun handleFraudCheckCompleted(event: FraudCheckCompletedEvent) {
        logger.info("Handling fraud check completed for payment: ${event.paymentId}, approved: ${event.approved}")

        val payment = paymentRepository.findById(event.paymentId).orElseThrow {
            IllegalArgumentException("Payment not found: ${event.paymentId}")
        }

        if (event.approved) {
            // Fraud check approved - proceed to funds reservation
            paymentStateMachineService.sendEvent(payment.id, PaymentEvent.FRAUD_CHECK_APPROVED)
            payment.state = PaymentState.FRAUD_CHECK_COMPLETED
            payment.markStep("FUNDS_RESERVATION")

            // Request funds reservation
            val fundsEvent = FundsReservationRequestedEvent(
                paymentId = payment.id,
                amount = payment.amount,
                currency = payment.currency,
                customerId = payment.customerId
            )

            kafkaTemplate.send(
                MessageBuilder.withPayload(fundsEvent)
                    .setHeader("paymentId", payment.id.toString())
                    .setHeader("correlationId", payment.correlationId)
                    .setHeader("traceId", payment.traceId)
                    .setHeader("eventType", "FundsReservationRequestedEvent")
                    .build()
            )

            logger.info("Fraud check approved, proceeding to funds reservation: ${payment.id}")
        } else {
            // Fraud check declined - fail payment
            paymentStateMachineService.sendEvent(payment.id, PaymentEvent.FRAUD_CHECK_DECLINED)
            payment.state = PaymentState.FRAUD_CHECK_FAILED
            payment.markFailed("Fraud check declined: ${event.declineReason}")

            // Publish payment failed event
            publishPaymentFailed(payment, "FRAUD_CHECK", event.declineReason ?: "Fraud check declined")

            logger.warn("Fraud check declined for payment: ${payment.id}, reason: ${event.declineReason}")
        }

        paymentRepository.save(payment)
    }

    @Transactional
    fun handleFundsReserved(event: FundsReservedEvent) {
        logger.info("Handling funds reserved for payment: ${event.paymentId}")

        val payment = paymentRepository.findById(event.paymentId).orElseThrow {
            IllegalArgumentException("Payment not found: ${event.paymentId}")
        }

        paymentStateMachineService.sendEvent(payment.id, PaymentEvent.FUNDS_RESERVED)
        payment.state = PaymentState.FUNDS_RESERVED
        payment.reservationId = event.reservationId
        payment.markStep("PAYMENT_EXECUTION")

        // Request payment execution
        val processorEvent = PaymentExecutionRequestedEvent(
            paymentId = payment.id,
            amount = payment.amount,
            currency = payment.currency,
            merchantId = payment.merchantId,
            customerId = payment.customerId
        )

        kafkaTemplate.send(
            MessageBuilder.withPayload(processorEvent)
                .setHeader("paymentId", payment.id.toString())
                .setHeader("correlationId", payment.correlationId)
                .setHeader("traceId", payment.traceId)
                .setHeader("eventType", "PaymentExecutionRequestedEvent")
                .build()
        )

        paymentRepository.save(payment)
        logger.info("Funds reserved, proceeding to payment execution: ${payment.id}")
    }

    @Transactional
    fun handleFundsReservationFailed(event: FundsReservationFailedEvent) {
        logger.warn("Handling funds reservation failed for payment: ${event.paymentId}, reason: ${event.reason}")

        val payment = paymentRepository.findById(event.paymentId).orElseThrow {
            IllegalArgumentException("Payment not found: ${event.paymentId}")
        }

        paymentStateMachineService.sendEvent(payment.id, PaymentEvent.FUNDS_RESERVATION_FAILED)
        payment.state = PaymentState.FUNDS_RESERVATION_FAILED
        payment.markFailed("Funds reservation failed: ${event.reason}")

        // Check if we should retry
        if (event.canRetry && payment.canRetry()) {
            logger.info("Scheduling retry for funds reservation: ${payment.id}")
            retryService.scheduleRetry(payment.id, "FUNDS_RESERVATION", event.reason)
        } else {
            // Trigger compensation
            compensationService.triggerCompensation(payment, "FUNDS_RESERVATION", event.reason)
        }

        paymentRepository.save(payment)
    }

    @Transactional
    fun handlePaymentExecuted(event: PaymentExecutedEvent) {
        logger.info("Handling payment executed for payment: ${event.paymentId}")

        val payment = paymentRepository.findById(event.paymentId).orElseThrow {
            IllegalArgumentException("Payment not found: ${event.paymentId}")
        }

        paymentStateMachineService.sendEvent(payment.id, PaymentEvent.PROCESSOR_EXECUTED)
        payment.state = PaymentState.PROCESSOR_EXECUTED
        payment.transactionId = event.transactionId
        payment.markStep("LEDGER_UPDATE")

        // Request ledger update
        val ledgerEvent = LedgerUpdateRequestedEvent(
            paymentId = payment.id,
            amount = payment.amount,
            currency = payment.currency,
            merchantId = payment.merchantId,
            transactionId = event.transactionId
        )

        kafkaTemplate.send(
            MessageBuilder.withPayload(ledgerEvent)
                .setHeader("paymentId", payment.id.toString())
                .setHeader("correlationId", payment.correlationId)
                .setHeader("traceId", payment.traceId)
                .setHeader("eventType", "LedgerUpdateRequestedEvent")
                .build()
        )

        paymentRepository.save(payment)
        logger.info("Payment executed, proceeding to ledger update: ${payment.id}")
    }

    @Transactional
    fun handlePaymentExecutionFailed(event: PaymentExecutionFailedEvent) {
        logger.warn("Handling payment execution failed for payment: ${event.paymentId}, reason: ${event.reason}")

        val payment = paymentRepository.findById(event.paymentId).orElseThrow {
            IllegalArgumentException("Payment not found: ${event.paymentId}")
        }

        paymentStateMachineService.sendEvent(payment.id, PaymentEvent.PROCESSOR_EXECUTION_FAILED)
        payment.state = PaymentState.PROCESSOR_EXECUTION_FAILED
        payment.markFailed("Payment execution failed: ${event.reason}")

        // Check if we should retry
        if (event.canRetry && payment.canRetry()) {
            logger.info("Scheduling retry for payment execution: ${payment.id}")
            retryService.scheduleRetry(payment.id, "PAYMENT_EXECUTION", event.reason)
        } else {
            // Trigger compensation
            compensationService.triggerCompensation(payment, "PAYMENT_EXECUTION", event.reason)
        }

        paymentRepository.save(payment)
    }

    @Transactional
    fun handleLedgerUpdated(event: LedgerUpdatedEvent) {
        logger.info("Handling ledger updated for payment: ${event.paymentId}")

        val payment = paymentRepository.findById(event.paymentId).orElseThrow {
            IllegalArgumentException("Payment not found: ${event.paymentId}")
        }

        paymentStateMachineService.sendEvent(payment.id, PaymentEvent.LEDGER_UPDATED)
        payment.state = PaymentState.COMPLETED
        payment.ledgerEntryId = event.ledgerEntryId
        payment.markCompleted()

        // Publish payment completed event
        val completedEvent = PaymentCompletedEvent(
            paymentId = payment.id,
            transactionId = payment.transactionId!!,
            ledgerEntryId = event.ledgerEntryId
        )

        kafkaTemplate.send(
            MessageBuilder.withPayload(completedEvent)
                .setHeader("paymentId", payment.id.toString())
                .setHeader("correlationId", payment.correlationId)
                .setHeader("traceId", payment.traceId)
                .setHeader("eventType", "PaymentCompletedEvent")
                .build()
        )

        paymentRepository.save(payment)
        logger.info("Payment completed successfully: ${payment.id}")
    }

    @Transactional
    fun handleLedgerUpdateFailed(event: LedgerUpdateFailedEvent) {
        logger.warn("Handling ledger update failed for payment: ${event.paymentId}, reason: ${event.reason}")

        val payment = paymentRepository.findById(event.paymentId).orElseThrow {
            IllegalArgumentException("Payment not found: ${event.paymentId}")
        }

        paymentStateMachineService.sendEvent(payment.id, PaymentEvent.LEDGER_UPDATE_FAILED)
        payment.state = PaymentState.LEDGER_UPDATE_FAILED
        payment.markFailed("Ledger update failed: ${event.reason}")

        // Check if we should retry
        if (event.canRetry && payment.canRetry()) {
            logger.info("Scheduling retry for ledger update: ${payment.id}")
            retryService.scheduleRetry(payment.id, "LEDGER_UPDATE", event.reason)
        } else {
            // Trigger compensation
            compensationService.triggerCompensation(payment, "LEDGER_UPDATE", event.reason)
        }

        paymentRepository.save(payment)
    }

    @Transactional
    fun handleCompensationCompleted(event: CompensationCompletedEvent) {
        logger.info("Handling compensation completed for payment: ${event.paymentId}, service: ${event.service}")

        val payment = paymentRepository.findById(event.paymentId).orElseThrow {
            IllegalArgumentException("Payment not found: ${event.paymentId}")
        }

        // Check if all compensations are completed
        // In a real implementation, you might track which services have completed compensation
        // For simplicity, we'll mark as compensated when we receive any compensation completed

        paymentStateMachineService.sendEvent(payment.id, PaymentEvent.COMPENSATION_COMPLETED)
        payment.state = PaymentState.COMPENSATED
        payment.markCompensated("Payment compensated due to failure")

        // Publish payment compensated event
        val compensatedEvent = PaymentCompensatedEvent(
            paymentId = payment.id,
            reason = payment.compensationReason ?: "Unknown reason"
        )

        kafkaTemplate.send(
            MessageBuilder.withPayload(compensatedEvent)
                .setHeader("paymentId", payment.id.toString())
                .setHeader("correlationId", payment.correlationId)
                .setHeader("traceId", payment.traceId)
                .setHeader("eventType", "PaymentCompensatedEvent")
                .build()
        )

        paymentRepository.save(payment)
        logger.info("Payment compensation completed: ${payment.id}")
    }

    private fun publishPaymentFailed(payment: Payment, failedStep: String, reason: String) {
        val failedEvent = PaymentFailedEvent(
            paymentId = payment.id,
            reason = reason,
            failedStep = failedStep
        )

        kafkaTemplate.send(
            MessageBuilder.withPayload(failedEvent)
                .setHeader("paymentId", payment.id.toString())
                .setHeader("correlationId", payment.correlationId)
                .setHeader("traceId", payment.traceId)
                .setHeader("eventType", "PaymentFailedEvent")
                .build()
        )
    }

    fun getPaymentStatus(paymentId: UUID): Payment? {
        return paymentRepository.findById(paymentId).orElse(null)
    }

    fun getPaymentsByMerchant(merchantId: String): List<Payment> {
        return paymentRepository.findByMerchantId(merchantId)
    }

    fun getPaymentsByCustomer(customerId: String): List<Payment> {
        return paymentRepository.findByCustomerId(customerId)
    }
}