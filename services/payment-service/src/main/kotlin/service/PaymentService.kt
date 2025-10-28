package dev.jkiakumbo.paymentorchestrator.service

import dev.jkiakumbo.paymentorchestrator.domain.Payment
import dev.jkiakumbo.paymentorchestrator.domain.PaymentRepository
import dev.jkiakumbo.paymentorchestrator.events.*
import dev.jkiakumbo.paymentorchestrator.state.PaymentEvent
import dev.jkiakumbo.paymentorchestrator.state.PaymentState
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.messaging.support.MessageBuilder
import org.springframework.statemachine.service.StateMachineService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Service
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val stateMachineService: StateMachineService<PaymentState, PaymentEvent>
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun initiatePayment(amount: BigDecimal, currency: String, merchantId: String, customerId: String): UUID {
        val paymentId = UUID.randomUUID()

        val payment = Payment(
            id = paymentId,
            amount = amount,
            currency = currency,
            merchantId = merchantId,
            customerId = customerId,
            state = PaymentState.INITIATED
        )

        paymentRepository.save(payment)

        // Start state machine
        val stateMachine = stateMachineService.acquireStateMachine(paymentId.toString())
        stateMachine.start()

        // Publish payment created event
        val event = PaymentCreatedEvent(
            paymentId = paymentId,
            amount = amount,
            currency = currency,
            merchantId = merchantId,
            customerId = customerId
        )

        kafkaTemplate.send(
            MessageBuilder.withPayload(event)
                .setHeader("paymentId", paymentId.toString())
                .setHeader("eventType", "PaymentCreatedEvent")
                .build()
        )

        logger.info("Payment initiated: $paymentId")
        return paymentId
    }

    @Transactional
    fun handleFraudCheckCompleted(event: FraudCheckCompletedEvent) {
        val payment = paymentRepository.findById(event.paymentId).orElseThrow {
            IllegalArgumentException("Payment not found: ${event.paymentId}")
        }

        val stateMachine = stateMachineService.acquireStateMachine(payment.id.toString())

        if (event.approved) {
            stateMachine.sendEvent(PaymentEvent.FRAUD_CHECK_APPROVED)
            payment.state = PaymentState.FRAUD_CHECK_COMPLETED

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
                    .setHeader("eventType", "FundsReservationRequestedEvent")
                    .build()
            )
        } else {
            stateMachine.sendEvent(PaymentEvent.FRAUD_CHECK_DECLINED)
            payment.state = PaymentState.FRAUD_CHECK_FAILED
            payment.failureReason = event.reason
        }

        payment.updatedAt = java.time.LocalDateTime.now()
        paymentRepository.save(payment)
    }

    @Transactional
    fun handleFundsReserved(event: FundsReservedEvent) {
        val payment = paymentRepository.findById(event.paymentId).orElseThrow {
            IllegalArgumentException("Payment not found: ${event.paymentId}")
        }

        val stateMachine = stateMachineService.acquireStateMachine(payment.id.toString())
        stateMachine.sendEvent(PaymentEvent.FUNDS_RESERVED)
        payment.state = PaymentState.FUNDS_RESERVED

        // Request payment execution
        val executionEvent = PaymentExecutionRequestedEvent(
            paymentId = payment.id,
            amount = payment.amount,
            currency = payment.currency,
            merchantId = payment.merchantId,
            customerId = payment.customerId
        )

        kafkaTemplate.send(
            MessageBuilder.withPayload(executionEvent)
                .setHeader("paymentId", payment.id.toString())
                .setHeader("eventType", "PaymentExecutionRequestedEvent")
                .build()
        )

        payment.updatedAt = java.time.LocalDateTime.now()
        paymentRepository.save(payment)
    }

    @Transactional
    fun handleFundsReservationFailed(event: FundsReservationFailedEvent) {
        val payment = paymentRepository.findById(event.paymentId).orElseThrow {
            IllegalArgumentException("Payment not found: ${event.paymentId}")
        }

        val stateMachine = stateMachineService.acquireStateMachine(payment.id.toString())
        stateMachine.sendEvent(PaymentEvent.FUNDS_RESERVATION_FAILED)
        payment.state = PaymentState.FUNDS_RESERVATION_FAILED
        payment.failureReason = event.reason

        // Trigger compensation
        triggerCompensation(payment, "FUNDS_RESERVATION", event.reason)

        payment.updatedAt = java.time.LocalDateTime.now()
        paymentRepository.save(payment)
    }

    @Transactional
    fun handlePaymentExecuted(event: PaymentExecutedEvent) {
        val payment = paymentRepository.findById(event.paymentId).orElseThrow {
            IllegalArgumentException("Payment not found: ${event.paymentId}")
        }

        val stateMachine = stateMachineService.acquireStateMachine(payment.id.toString())
        stateMachine.sendEvent(PaymentEvent.PROCESSOR_EXECUTED)
        payment.state = PaymentState.PROCESSOR_EXECUTED

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
                .setHeader("eventType", "LedgerUpdateRequestedEvent")
                .build()
        )

        payment.updatedAt = java.time.LocalDateTime.now()
        paymentRepository.save(payment)
    }

    @Transactional
    fun handlePaymentExecutionFailed(event: PaymentExecutionFailedEvent) {
        val payment = paymentRepository.findById(event.paymentId).orElseThrow {
            IllegalArgumentException("Payment not found: ${event.paymentId}")
        }

        val stateMachine = stateMachineService.acquireStateMachine(payment.id.toString())
        stateMachine.sendEvent(PaymentEvent.PROCESSOR_EXECUTION_FAILED)
        payment.state = PaymentState.PROCESSOR_EXECUTION_FAILED
        payment.failureReason = event.reason

        // Trigger compensation
        triggerCompensation(payment, "PAYMENT_EXECUTION", event.reason)

        payment.updatedAt = java.time.LocalDateTime.now()
        paymentRepository.save(payment)
    }

    @Transactional
    fun handleLedgerUpdated(event: LedgerUpdatedEvent) {
        val payment = paymentRepository.findById(event.paymentId).orElseThrow {
            IllegalArgumentException("Payment not found: ${event.paymentId}")
        }

        val stateMachine = stateMachineService.acquireStateMachine(payment.id.toString())
        stateMachine.sendEvent(PaymentEvent.LEDGER_UPDATED)
        payment.state = PaymentState.COMPLETED

        payment.updatedAt = java.time.LocalDateTime.now()
        paymentRepository.save(payment)

        logger.info("Payment completed successfully: ${payment.id}")
    }

    @Transactional
    fun handleLedgerUpdateFailed(event: LedgerUpdateFailedEvent) {
        val payment = paymentRepository.findById(event.paymentId).orElseThrow {
            IllegalArgumentException("Payment not found: ${event.paymentId}")
        }

        val stateMachine = stateMachineService.acquireStateMachine(payment.id.toString())
        stateMachine.sendEvent(PaymentEvent.LEDGER_UPDATE_FAILED)
        payment.state = PaymentState.LEDGER_UPDATE_FAILED
        payment.failureReason = event.reason

        // Trigger compensation
        triggerCompensation(payment, "LEDGER_UPDATE", event.reason)

        payment.updatedAt = java.time.LocalDateTime.now()
        paymentRepository.save(payment)
    }

    private fun triggerCompensation(payment: Payment, failedStep: String, reason: String) {
        val compensationEvent = CompensationTriggeredEvent(
            paymentId = payment.id,
            failedStep = failedStep,
            reason = reason
        )

        kafkaTemplate.send(
            MessageBuilder.withPayload(compensationEvent)
                .setHeader("paymentId", payment.id.toString())
                .setHeader("eventType", "CompensationTriggeredEvent")
                .build()
        )

        logger.warn("Compensation triggered for payment: ${payment.id}, failed step: $failedStep, reason: $reason")
    }

    fun getPaymentStatus(paymentId: UUID): Payment? {
        return paymentRepository.findById(paymentId).orElse(null)
    }
}