package dev.jkiakumbo.paymentorchestrator.fraudservice.service

import dev.jkiakumbo.paymentorchestrator.fraudservice.domain.FraudCheck
import dev.jkiakumbo.paymentorchestrator.fraudservice.domain.FraudCheckRepository
import dev.jkiakumbo.paymentorchestrator.fraudservice.domain.FraudCheckStatus
import dev.jkiakumbo.paymentorchestrator.fraudservice.events.FraudCheckCompletedEvent
import dev.jkiakumbo.paymentorchestrator.fraudservice.events.FraudCheckRequestedEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class FraudDetectionService(
    private val fraudCheckRepository: FraudCheckRepository,
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun processFraudCheck(event: FraudCheckRequestedEvent) {
        logger.info("Processing fraud check for payment: ${event.paymentId}")

        // Check if we already processed this payment (idempotency)
        val existingCheck = fraudCheckRepository.findByPaymentId(event.paymentId)
        if (existingCheck != null) {
            logger.info("Fraud check already processed for payment: ${event.paymentId}")
            return
        }

        val fraudCheck = FraudCheck(
            paymentId = event.paymentId,
            amount = event.amount,
            currency = event.currency,
            merchantId = event.merchantId,
            customerId = event.customerId,
            status = FraudCheckStatus.PENDING
        )

        fraudCheckRepository.save(fraudCheck)

        // Mock fraud detection logic
        val approved = performFraudCheck(event)
        val status = if (approved) FraudCheckStatus.APPROVED else FraudCheckStatus.DECLINED
        val reason = if (!approved) "High risk transaction detected" else null

        fraudCheck.status = status
        fraudCheck.reason = reason
        fraudCheck.updatedAt = java.time.LocalDateTime.now()
        fraudCheckRepository.save(fraudCheck)

        // Publish result
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

    private fun performFraudCheck(event: FraudCheckRequestedEvent): Boolean {
        // Mock fraud detection rules
        return when {
            event.amount > java.math.BigDecimal("10000") -> false
            event.merchantId.contains("BLACKLIST") -> false
            event.customerId.startsWith("SUSPECT") -> false
            else -> true
        }
    }
}