package dev.jkiakumbo.paymentorchestrator.fundsservice.consumers


import com.fasterxml.jackson.databind.ObjectMapper
import dev.jkiakumbo.paymentorchestrator.fundsservice.events.CompensationRequestedEvent
import dev.jkiakumbo.paymentorchestrator.fundsservice.events.FundsReservationRequestedEvent
import dev.jkiakumbo.paymentorchestrator.fundsservice.service.CompensationService
import dev.jkiakumbo.paymentorchestrator.fundsservice.service.FundsService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component

@Component
class FundsEventConsumer(
    private val fundsService: FundsService,
    private val compensationService: CompensationService,
    private val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["funds-reservation-requests"],
        groupId = "funds-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Retryable(
        retryFor = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 1000, multiplier = 2.0)
    )
    fun handleFundsReservationRequest(
        @Payload payload: String,
        record: ConsumerRecord<String, String>,
        ack: Acknowledgment
    ) {
        try {
            logger.info("Received funds reservation request: ${record.key()}")

            val event = objectMapper.readValue(payload, FundsReservationRequestedEvent::class.java)
            fundsService.reserveFunds(event)

            ack.acknowledge()
            logger.info("Successfully processed funds reservation request: ${event.paymentId}")
        } catch (e: Exception) {
            logger.error("Failed to process funds reservation request: ${record.key()}", e)
            throw e // Will trigger retry
        }
    }

    @KafkaListener(
        topics = ["compensation-requests"],
        groupId = "funds-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun handleCompensationRequest(
        @Payload payload: String,
        record: ConsumerRecord<String, String>,
        ack: Acknowledgment
    ) {
        try {
            logger.info("Received compensation request: ${record.key()}")

            val event = objectMapper.readValue(payload, CompensationRequestedEvent::class.java)

            // Always handle compensation for funds service to release reserved funds
            compensationService.handleCompensation(event)

            ack.acknowledge()
            logger.info("Successfully processed compensation request: ${event.paymentId}")
        } catch (e: Exception) {
            logger.error("Failed to process compensation request: ${record.key()}", e)
            // For compensation, we ack even on error to avoid blocking the compensation flow
            ack.acknowledge()
        }
    }

    @KafkaListener(
        topics = ["funds-commit-requests"],
        groupId = "funds-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun handleFundsCommitRequest(
        @Payload payload: String,
        record: ConsumerRecord<String, String>,
        ack: Acknowledgment
    ) {
        try {
            logger.info("Received funds commit request: ${record.key()}")

            // This would be called when payment is fully completed to commit the reserved funds
            val paymentId = objectMapper.readValue(payload, java.util.UUID::class.java)
            fundsService.commitFunds(paymentId)

            ack.acknowledge()
            logger.info("Successfully committed funds for payment: $paymentId")
        } catch (e: Exception) {
            logger.error("Failed to commit funds for payment: ${record.key()}", e)
            ack.acknowledge() // Ack to avoid reprocessing
        }
    }
}