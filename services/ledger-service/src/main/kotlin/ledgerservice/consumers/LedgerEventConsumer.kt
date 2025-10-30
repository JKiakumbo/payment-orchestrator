package dev.jkiakumbo.paymentorchestrator.ledgerservice.consumers

import com.fasterxml.jackson.databind.ObjectMapper
import dev.jkiakumbo.paymentorchestrator.ledgerservice.events.CompensationRequestedEvent
import dev.jkiakumbo.paymentorchestrator.ledgerservice.events.LedgerUpdateRequestedEvent
import dev.jkiakumbo.paymentorchestrator.ledgerservice.service.CompensationService
import dev.jkiakumbo.paymentorchestrator.ledgerservice.service.LedgerService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component

@Component
class LedgerEventConsumer(
    private val ledgerService: LedgerService,
    private val compensationService: CompensationService,
    private val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["ledger-update-requests"],
        groupId = "ledger-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Retryable(
        retryFor = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 1000, multiplier = 2.0)
    )
    fun handleLedgerUpdateRequest(
        @Payload payload: String,
        record: ConsumerRecord<String, String>,
        ack: Acknowledgment
    ) {
        try {
            logger.info("Received ledger update request: ${record.key()}")

            val event = objectMapper.readValue(payload, LedgerUpdateRequestedEvent::class.java)
            ledgerService.processLedgerUpdate(event)

            ack.acknowledge()
            logger.info("Successfully processed ledger update request: ${event.paymentId}")
        } catch (e: Exception) {
            logger.error("Failed to process ledger update request: ${record.key()}", e)
            throw e // Will trigger retry
        }
    }

    @KafkaListener(
        topics = ["compensation-requests"],
        groupId = "ledger-service",
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

            // Only handle compensation if it's related to ledger update or general compensation
            if (event.failedStep == "LEDGER_UPDATE" || event.failedStep == "ALL") {
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