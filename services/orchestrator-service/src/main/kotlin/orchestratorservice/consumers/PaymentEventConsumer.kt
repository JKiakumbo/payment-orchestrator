package dev.jkiakumbo.paymentorchestrator.orchestratorservice.consumers

import com.fasterxml.jackson.databind.ObjectMapper
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.events.CompensationCompletedEvent
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.events.FraudCheckCompletedEvent
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.events.FundsReservationFailedEvent
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.events.FundsReservedEvent
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.events.LedgerUpdateFailedEvent
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.events.LedgerUpdatedEvent
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.events.PaymentExecutedEvent
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.events.PaymentExecutionFailedEvent
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.service.OrchestrationService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component

@Component
class PaymentEventConsumer(
    private val paymentOrchestrationService: OrchestrationService,
    private val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["fraud-check-results"],
        groupId = "payment-orchestrator",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Retryable(
        retryFor = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 1000, multiplier = 2.0)
    )
    fun handleFraudCheckResults(
        @Payload payload: String,
        @Header("paymentId") paymentId: String,
        record: ConsumerRecord<String, String>,
        ack: Acknowledgment
    ) {
        try {
            logger.info("Received fraud check result for payment: $paymentId")

            val event = objectMapper.readValue(payload, FraudCheckCompletedEvent::class.java)
            paymentOrchestrationService.handleFraudCheckCompleted(event)

            ack.acknowledge()
            logger.info("Successfully processed fraud check result for payment: $paymentId")
        } catch (e: Exception) {
            logger.error("Failed to process fraud check result for payment: $paymentId", e)
            throw e // Will trigger retry
        }
    }

    @KafkaListener(
        topics = ["funds-reservation-results"],
        groupId = "payment-orchestrator",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun handleFundsReservationResults(
        @Payload payload: String,
        @Header("paymentId") paymentId: String,
        @Header("eventType") eventType: String,
        record: ConsumerRecord<String, String>,
        ack: Acknowledgment
    ) {
        try {
            logger.info("Received funds reservation result for payment: $paymentId, eventType: $eventType")

            when (eventType) {
                "FundsReservedEvent" -> {
                    val event = objectMapper.readValue(payload, FundsReservedEvent::class.java)
                    paymentOrchestrationService.handleFundsReserved(event)
                }
                "FundsReservationFailedEvent" -> {
                    val event = objectMapper.readValue(payload, FundsReservationFailedEvent::class.java)
                    paymentOrchestrationService.handleFundsReservationFailed(event)
                }
                else -> {
                    logger.warn("Unknown funds reservation event type: $eventType for payment: $paymentId")
                }
            }

            ack.acknowledge()
            logger.info("Successfully processed funds reservation result for payment: $paymentId")
        } catch (e: Exception) {
            logger.error("Failed to process funds reservation result for payment: $paymentId", e)
            ack.acknowledge()
        }
    }

    @KafkaListener(
        topics = ["payment-execution-results"],
        groupId = "payment-orchestrator",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun handlePaymentExecutionResults(
        @Payload payload: String,
        @Header("paymentId") paymentId: String,
        @Header("eventType") eventType: String,
        record: ConsumerRecord<String, String>,
        ack: Acknowledgment
    ) {
        try {
            logger.info("Received payment execution result for payment: $paymentId, eventType: $eventType")

            when (eventType) {
                "PaymentExecutedEvent" -> {
                    val event = objectMapper.readValue(payload, PaymentExecutedEvent::class.java)
                    paymentOrchestrationService.handlePaymentExecuted(event)
                }
                "PaymentExecutionFailedEvent" -> {
                    val event = objectMapper.readValue(payload, PaymentExecutionFailedEvent::class.java)
                    paymentOrchestrationService.handlePaymentExecutionFailed(event)
                }
                else -> {
                    logger.warn("Unknown payment execution event type: $eventType for payment: $paymentId")
                }
            }

            ack.acknowledge()
            logger.info("Successfully processed payment execution result for payment: $paymentId")
        } catch (e: Exception) {
            logger.error("Failed to process payment execution result for payment: $paymentId", e)
            ack.acknowledge()
        }
    }

    @KafkaListener(
        topics = ["ledger-update-results"],
        groupId = "payment-orchestrator",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun handleLedgerUpdateResults(
        @Payload payload: String,
        @Header("paymentId") paymentId: String,
        @Header("eventType") eventType: String,
        record: ConsumerRecord<String, String>,
        ack: Acknowledgment
    ) {
        try {
            logger.info("Received ledger update result for payment: $paymentId, eventType: $eventType")

            when (eventType) {
                "LedgerUpdatedEvent" -> {
                    val event = objectMapper.readValue(payload, LedgerUpdatedEvent::class.java)
                    paymentOrchestrationService.handleLedgerUpdated(event)
                }
                "LedgerUpdateFailedEvent" -> {
                    val event = objectMapper.readValue(payload, LedgerUpdateFailedEvent::class.java)
                    paymentOrchestrationService.handleLedgerUpdateFailed(event)
                }
                else -> {
                    logger.warn("Unknown ledger update event type: $eventType for payment: $paymentId")
                }
            }

            ack.acknowledge()
            logger.info("Successfully processed ledger update result for payment: $paymentId")
        } catch (e: Exception) {
            logger.error("Failed to process ledger update result for payment: $paymentId", e)
            ack.acknowledge()
        }
    }

    @KafkaListener(
        topics = ["compensation-completed"],
        groupId = "payment-orchestrator",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun handleCompensationCompleted(
        @Payload payload: String,
        @Header("paymentId") paymentId: String,
        record: ConsumerRecord<String, String>,
        ack: Acknowledgment
    ) {
        try {
            logger.info("Received compensation completed for payment: $paymentId")

            val event = objectMapper.readValue(payload, CompensationCompletedEvent::class.java)
            paymentOrchestrationService.handleCompensationCompleted(event)

            ack.acknowledge()
            logger.info("Successfully processed compensation completed for payment: $paymentId")
        } catch (e: Exception) {
            logger.error("Failed to process compensation completed for payment: $paymentId", e)
            ack.acknowledge()
        }
    }
}