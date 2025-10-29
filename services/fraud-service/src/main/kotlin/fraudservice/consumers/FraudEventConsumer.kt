package dev.jkiakumbo.paymentorchestrator.fraudservice.consumers

import dev.jkiakumbo.paymentorchestrator.fraudservice.events.FraudCheckRequestedEvent
import dev.jkiakumbo.paymentorchestrator.fraudservice.service.FraudDetectionService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component

@Component
class FraudEventConsumer(
    private val fraudDetectionService: FraudDetectionService
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["fraud-check-requests"])
    fun handleFraudCheckRequest(
        @Payload event: FraudCheckRequestedEvent,
        @Header("paymentId") paymentId: String
    ) {
        logger.info("Received fraud check request for payment: $paymentId")
        fraudDetectionService.processFraudCheck(event)
    }
}