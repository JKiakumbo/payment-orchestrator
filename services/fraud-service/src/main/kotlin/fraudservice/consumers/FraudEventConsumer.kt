package dev.jkiakumbo.paymentorchestrator.fraudservice.consumers

import com.fasterxml.jackson.databind.ObjectMapper
import dev.jkiakumbo.paymentorchestrator.fraudservice.events.CompensationRequestedEvent
import dev.jkiakumbo.paymentorchestrator.fraudservice.events.FraudCheckRequestedEvent
import dev.jkiakumbo.paymentorchestrator.fraudservice.service.CompensationService
import dev.jkiakumbo.paymentorchestrator.fraudservice.service.FraudDetectionService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component

@Component
class FraudEventConsumer(
    private val fraudDetectionService: FraudDetectionService,
    private val compensationService: CompensationService,
    private val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["fraud-check-requests"],
        groupId = "fraud-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Retryable(
        retryFor = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 1000, multiplier = 2.0)
    )
    fun handleFraudCheckRequest(
        @Payload payload: String,
        record: ConsumerRecord<String, String>,
        ack: Acknowledgment
    ) {
        try {
            logger.info("Received fraud check request: ${record.key()}")

            val event = objectMapper.readValue(payload, FraudCheckRequestedEvent::class.java)
            fraudDetectionService.processFraudCheck(event)

            ack.acknowledge()
            logger.info("Successfully processed fraud check request: ${event.paymentId}")
        } catch (e: Exception) {
            logger.error("Failed to process fraud check request: ${record.key()}", e)
            throw e // Will trigger retry
        }
    }

    @KafkaListener(
        topics = ["compensation-requests"],
        groupId = "fraud-service",
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

            // Only handle compensation if it's related to fraud check or general compensation
            if (event.failedStep == "FRAUD_CHECK" || event.failedStep == "ALL") {
                compensationService.handleCompensation(event)
            }

            ack.acknowledge()
            logger.info("Successfully processed compensation request: ${event.paymentId}")
        } catch (e: Exception) {
            logger.error("Failed to process compensation request: ${record.key()}", e)
            // For compensation, we ack even on error to avoid blocking the compensation flow
            ack.acknowledge()
        }
    }
}
