package dev.jkiakumbo.paymentorchestrator.fraudservice.service


import dev.jkiakumbo.paymentorchestrator.fraudservice.events.FraudCheckCompletedEvent
import dev.jkiakumbo.paymentorchestrator.fraudservice.events.FraudCheckRequestedEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class FraudService(
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["fraud-check-requests"])
    fun handleFraudCheckRequest(event: FraudCheckRequestedEvent) {
        logger.info("Processing fraud check for payment: ${event.paymentId}")

        // Mock fraud check logic
        val approved = when {
            event.amount > BigDecimal("10000") -> false // High amount
            event.customerId.startsWith("SUSPECT") -> false // Suspicious customer
            else -> true
        }

        val reason = if (!approved) "Fraud check failed: high risk transaction" else null

        val resultEvent = FraudCheckCompletedEvent(
            paymentId = event.paymentId,
            approved = approved,
            reason = reason
        )

        kafkaTemplate.send(
            MessageBuilder.withPayload(resultEvent)
                .setHeader("paymentId", event.paymentId.toString())
                .setHeader("eventType", "FraudCheckCompletedEvent")
                .build()
        )

        logger.info("Fraud check completed for payment: ${event.paymentId}, approved: $approved")
    }
}