package dev.jkiakumbo.paymentorchestrator.consumers

import dev.jkiakumbo.paymentorchestrator.events.CompensationCompletedEvent
import dev.jkiakumbo.paymentorchestrator.events.CompensationTriggeredEvent
import dev.jkiakumbo.paymentorchestrator.events.FraudCheckCompletedEvent
import dev.jkiakumbo.paymentorchestrator.events.FraudCheckRequestedEvent
import dev.jkiakumbo.paymentorchestrator.events.FundsReservationFailedEvent
import dev.jkiakumbo.paymentorchestrator.events.FundsReservedEvent
import dev.jkiakumbo.paymentorchestrator.events.LedgerUpdateFailedEvent
import dev.jkiakumbo.paymentorchestrator.events.LedgerUpdatedEvent
import dev.jkiakumbo.paymentorchestrator.events.PaymentCreatedEvent
import dev.jkiakumbo.paymentorchestrator.events.PaymentExecutedEvent
import dev.jkiakumbo.paymentorchestrator.events.PaymentExecutionFailedEvent
import dev.jkiakumbo.paymentorchestrator.service.PaymentService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component

@Component
class PaymentEventConsumer(
    private val paymentService: PaymentService
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["payment-events"])
    fun handlePaymentCreated(
        @Payload event: PaymentCreatedEvent,
        @Header("paymentId") paymentId: String
    ) {
        logger.info("Received PaymentCreatedEvent for payment: $paymentId")

        // Trigger fraud check
        val fraudCheckEvent = FraudCheckRequestedEvent(
            paymentId = event.paymentId,
            amount = event.amount,
            currency = event.currency,
            merchantId = event.merchantId,
            customerId = event.customerId
        )

        // In a real system, this would be sent to a fraud service topic
        logger.info("Fraud check requested for payment: $paymentId")
    }

    @KafkaListener(topics = ["fraud-events"])
    fun handleFraudCheckCompleted(
        @Payload event: FraudCheckCompletedEvent,
        @Header("paymentId") paymentId: String
    ) {
        logger.info("Received FraudCheckCompletedEvent for payment: $paymentId, approved: ${event.approved}")
        paymentService.handleFraudCheckCompleted(event)
    }

    @KafkaListener(topics = ["funds-events"])
    fun handleFundsEvents(
        @Payload event: Any,
        @Header("paymentId") paymentId: String,
        @Header("eventType") eventType: String
    ) {
        logger.info("Received funds event: $eventType for payment: $paymentId")

        when (event) {
            is FundsReservedEvent -> paymentService.handleFundsReserved(event)
            is FundsReservationFailedEvent -> paymentService.handleFundsReservationFailed(event)
        }
    }

    @KafkaListener(topics = ["processor-events"])
    fun handleProcessorEvents(
        @Payload event: Any,
        @Header("paymentId") paymentId: String,
        @Header("eventType") eventType: String
    ) {
        logger.info("Received processor event: $eventType for payment: $paymentId")

        when (event) {
            is PaymentExecutedEvent -> paymentService.handlePaymentExecuted(event)
            is PaymentExecutionFailedEvent -> paymentService.handlePaymentExecutionFailed(event)
        }
    }

    @KafkaListener(topics = ["ledger-events"])
    fun handleLedgerEvents(
        @Payload event: Any,
        @Header("paymentId") paymentId: String,
        @Header("eventType") eventType: String
    ) {
        logger.info("Received ledger event: $eventType for payment: $paymentId")

        when (event) {
            is LedgerUpdatedEvent -> paymentService.handleLedgerUpdated(event)
            is LedgerUpdateFailedEvent -> paymentService.handleLedgerUpdateFailed(event)
        }
    }

    @KafkaListener(topics = ["compensation-events"])
    fun handleCompensationEvents(
        @Payload event: Any,
        @Header("paymentId") paymentId: String,
        @Header("eventType") eventType: String
    ) {
        logger.info("Received compensation event: $eventType for payment: $paymentId")

        when (event) {
            is CompensationTriggeredEvent -> handleCompensationTriggered(event)
            is CompensationCompletedEvent -> handleCompensationCompleted(event)
        }
    }

    private fun handleCompensationTriggered(event: CompensationTriggeredEvent) {
        logger.warn("Processing compensation for payment: ${event.paymentId}, failed step: ${event.failedStep}")

        // In a real system, this would trigger specific compensation logic based on the failed step
        // For now, we'll just mark it as compensated

        val compensationCompleted = CompensationCompletedEvent(paymentId = event.paymentId)

        // TODO: Send compensation completed event
        // kafkaTemplate.send(...)

        logger.info("Compensation completed for payment: ${event.paymentId}")
    }

    private fun handleCompensationCompleted(event: CompensationCompletedEvent) {
        logger.info("Compensation process completed for payment: ${event.paymentId}")
    }
}