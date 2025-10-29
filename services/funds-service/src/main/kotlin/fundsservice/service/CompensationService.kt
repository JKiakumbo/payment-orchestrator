package dev.jkiakumbo.paymentorchestrator.fundsservice.service


import dev.jkiakumbo.paymentorchestrator.fundsservice.events.CompensationCompletedEvent
import dev.jkiakumbo.paymentorchestrator.fundsservice.events.CompensationRequestedEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class CompensationService(
    private val fundsService: FundsService,
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun handleCompensation(event: CompensationRequestedEvent) {
        logger.info("Processing compensation for payment: ${event.paymentId}, reason: ${event.reason}")

        try {
            // Always release funds for compensation, regardless of which step failed
            // This ensures we don't lock funds indefinitely
            fundsService.releaseFunds(event.paymentId, event.reason)

            publishCompensationCompleted(event.paymentId)
            logger.info("Compensation completed for payment: ${event.paymentId}")
        } catch (e: Exception) {
            logger.error("Compensation failed for payment: ${event.paymentId}", e)
            // Even if compensation fails, we mark it as completed to avoid blocking the saga
            // In production, you might want more sophisticated error handling
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