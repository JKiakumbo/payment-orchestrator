package dev.jkiakumbo.paymentorchestrator.fraudservice.service

import dev.jkiakumbo.paymentorchestrator.fraudservice.events.CompensationCompletedEvent
import dev.jkiakumbo.paymentorchestrator.fraudservice.events.CompensationRequestedEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Service
import java.util.*

@Service
class CompensationService(
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun handleCompensation(event: CompensationRequestedEvent) {
        logger.info("Processing compensation for payment: ${event.paymentId}, reason: ${event.reason}")

        try {
            // Fraud service compensation is typically no-op since we don't change state
            // that needs to be rolled back. We just mark the compensation as completed.
            logger.info("No compensation actions required for fraud service for payment: ${event.paymentId}")

            publishCompensationCompleted(event.paymentId)
        } catch (e: Exception) {
            logger.error("Compensation failed for payment: ${event.paymentId}", e)
            // Even if compensation fails, we mark it as completed to avoid blocking the saga
            publishCompensationCompleted(event.paymentId)
        }
    }

    private fun publishCompensationCompleted(paymentId: UUID) {
        val event = CompensationCompletedEvent(paymentId = paymentId)

        kafkaTemplate.send(
            MessageBuilder.withPayload(event)
                .setHeader("paymentId", paymentId.toString())
                .setHeader("eventType", "CompensationCompletedEvent")
                .setHeader("correlationId", UUID.randomUUID().toString())
                .build()
        )
    }
}