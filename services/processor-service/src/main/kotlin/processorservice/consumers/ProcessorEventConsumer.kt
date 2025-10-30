package dev.jkiakumbo.paymentorchestrator.processorservice.consumers

import com.fasterxml.jackson.databind.ObjectMapper
import dev.jkiakumbo.paymentorchestrator.processorservice.events.CompensationRequestedEvent
import dev.jkiakumbo.paymentorchestrator.processorservice.events.PaymentExecutionRequestedEvent
import dev.jkiakumbo.paymentorchestrator.processorservice.service.CompensationService
import dev.jkiakumbo.paymentorchestrator.processorservice.service.PaymentProcessorService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component

@Component
class ProcessorEventConsumer(
    private val paymentProcessorService: PaymentProcessorService,
    private val compensationService: CompensationService,
    private val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["payment-execution-requests"],
        groupId = "processor-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Retryable(
        retryFor = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 1000, multiplier = 2.0)
    )
    fun handlePaymentExecutionRequest(
        @Payload payload: String,
        record: ConsumerRecord<String, String>,
        ack: Acknowledgment
    ) {
        try {
            logger.info("Received payment execution request: ${record.key()}")

            val event = objectMapper.readValue(payload, PaymentExecutionRequestedEvent::class.java)
            paymentProcessorService.processPayment(event)

            ack.acknowledge()
            logger.info("Successfully processed payment execution request: ${event.paymentId}")
        } catch (e: Exception) {
            logger.error("Failed to process payment execution request: ${record.key()}", e)
            throw e // Will trigger retry
        }
    }

    @KafkaListener(
        topics = ["compensation-requests"],
        groupId = "processor-service",
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

            // Only handle compensation if it's related to processor or general compensation
            if (event.failedStep == "PROCESSOR_EXECUTION" || event.failedStep == "ALL") {
                compensationService.handleCompensation(event)
            }

            ack.acknowledge()
            logger.info("Successfully processed compensation request: ${event.paymentId}")
        } catch (e: Exception) {
            logger.error("Failed to process compensation request: ${record.key()}", e)
            // For compensation, we might want different error handling
            ack.acknowledge() // Ack even on error to avoid blocking compensation flow
        }
    }
}