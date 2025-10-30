package dev.jkiakumbo.paymentorchestrator.orchestratorservice.service
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.domain.Payment
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.events.CompensationTriggeredEvent
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

    fun triggerCompensation(payment: Payment, failedStep: String, reason: String) {
        logger.warn("Triggering compensation for payment: ${payment.id}, failed step: $failedStep, reason: $reason")

        val compensationEvent = CompensationTriggeredEvent(
            paymentId = payment.id,
            failedStep = failedStep,
            reason = reason
        )

        // Send compensation event to all services that might need to rollback
        kafkaTemplate.send(
            MessageBuilder.withPayload(compensationEvent)
                .setHeader("paymentId", payment.id.toString())
                .setHeader("correlationId", payment.correlationId)
                .setHeader("traceId", payment.traceId)
                .setHeader("eventType", "CompensationTriggeredEvent")
                .build()
        )

        logger.info("Compensation triggered for payment: ${payment.id}")
    }
}