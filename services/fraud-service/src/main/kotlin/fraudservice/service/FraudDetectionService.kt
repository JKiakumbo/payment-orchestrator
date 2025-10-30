package dev.jkiakumbo.paymentorchestrator.fraudservice.service

import dev.jkiakumbo.paymentorchestrator.fraudservice.domain.FraudCheck
import dev.jkiakumbo.paymentorchestrator.fraudservice.domain.FraudCheckRepository
import dev.jkiakumbo.paymentorchestrator.fraudservice.domain.FraudCheckStatus
import dev.jkiakumbo.paymentorchestrator.fraudservice.events.FraudCheckCompletedEvent
import dev.jkiakumbo.paymentorchestrator.fraudservice.events.FraudCheckFailedEvent
import dev.jkiakumbo.paymentorchestrator.fraudservice.events.FraudCheckRequestedEvent
import dev.jkiakumbo.paymentorchestrator.fraudservice.events.ManualReviewRequiredEvent
import dev.jkiakumbo.paymentorchestrator.fraudservice.exception.FraudCheckException
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.messaging.support.MessageBuilder
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class FraudDetectionService(
    private val fraudCheckRepository: FraudCheckRepository,
    private val fraudRuleEngine: FraudRuleEngine,
    private val riskAssessmentService: RiskAssessmentService,
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val compensationService: CompensationService
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun processFraudCheck(event: FraudCheckRequestedEvent) {
        logger.info("Processing fraud check for payment: ${event.paymentId}")

        // Check idempotency - if we already processed this payment
        val existingCheck = fraudCheckRepository.findByPaymentId(event.paymentId)
        if (existingCheck != null) {
            handleExistingCheck(existingCheck, event)
            return
        }

        // Create new fraud check record
        val fraudCheck = FraudCheck(
            paymentId = event.paymentId,
            amount = event.amount,
            currency = event.currency,
            merchantId = event.merchantId,
            customerId = event.customerId,
            status = FraudCheckStatus.PENDING
        )

        fraudCheckRepository.save(fraudCheck)

        // Process the fraud check
        processFraudCheckEvaluation(fraudCheck)
    }

    private fun handleExistingCheck(fraudCheck: FraudCheck, event: FraudCheckRequestedEvent) {
        logger.info("Fraud check already exists for payment: ${event.paymentId}, status: ${fraudCheck.status}")

        when (fraudCheck.status) {
            FraudCheckStatus.COMPLETED -> {
                // Idempotency - already succeeded, publish result event
                publishResultEvent(fraudCheck)
            }
            FraudCheckStatus.FAILED -> {
                if (fraudCheck.canRetry()) {
                    logger.info("Retrying failed fraud check: ${event.paymentId}, retry count: ${fraudCheck.retryCount}")
                    fraudCheck.incrementRetry()
                    fraudCheck.markAsProcessing()
                    fraudCheckRepository.save(fraudCheck)

                    // Retry processing
                    processFraudCheckEvaluation(fraudCheck)
                } else {
                    logger.warn("Fraud check ${event.paymentId} has exceeded maximum retry attempts")
                    // Do nothing - already failed and cannot retry
                }
            }
            FraudCheckStatus.PROCESSING -> {
                logger.info("Fraud check ${event.paymentId} is already being processed")
                // Do nothing - already processing
            }
            else -> {
                // For other statuses, retry processing
                processFraudCheckEvaluation(fraudCheck)
            }
        }
    }

    @Retryable(
        retryFor = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 1000, multiplier = 2.0)
    )
    @Transactional
    fun processFraudCheckEvaluation(fraudCheck: FraudCheck) {
        try {
            fraudCheck.markAsProcessing()
            fraudCheckRepository.save(fraudCheck)

            logger.info("Evaluating fraud rules for payment: ${fraudCheck.paymentId}")

            // Evaluate fraud rules
            val ruleResult = fraudRuleEngine.evaluateRules(
                FraudCheckRequestedEvent(
                    paymentId = fraudCheck.paymentId,
                    amount = fraudCheck.amount,
                    currency = fraudCheck.currency,
                    merchantId = fraudCheck.merchantId,
                    customerId = fraudCheck.customerId
                )
            )

            // Assess risk
            val riskAssessment = riskAssessmentService.assessRisk(ruleResult.totalRiskScore)

            // Update fraud check with results
            fraudCheck.markAsCompleted(
                approved = riskAssessment.isApproved,
                riskScore = ruleResult.totalRiskScore,
                riskLevel = riskAssessment.riskLevel,
                triggeredRules = ruleResult.triggeredRules
            )
            fraudCheck.evaluationTimeMs = ruleResult.evaluationTimeMs
            fraudCheckRepository.save(fraudCheck)

            // Check if manual review is required
            if (riskAssessmentService.shouldRequireManualReview(ruleResult.totalRiskScore, riskAssessment.riskLevel)) {
                publishManualReviewEvent(fraudCheck, ruleResult.triggeredRules)
                logger.info("Manual review required for payment: ${fraudCheck.paymentId}")
            } else {
                publishResultEvent(fraudCheck)
            }

            logger.info("Fraud check completed for payment: ${fraudCheck.paymentId}, " +
                    "approved: ${riskAssessment.isApproved}, risk score: ${ruleResult.totalRiskScore}")

        } catch (e: Exception) {
            logger.error("Fraud check evaluation failed for payment: ${fraudCheck.paymentId}", e)

            fraudCheck.markAsFailed(e.message ?: "Unknown error")
            fraudCheckRepository.save(fraudCheck)

            val canRetry = e !is FraudCheckException && fraudCheck.canRetry()

            publishFailureEvent(fraudCheck, e.message ?: "Unknown error", canRetry)
        }
    }

    @Transactional
    fun findStuckChecks(): List<FraudCheck> {
        val cutoff = java.time.LocalDateTime.now().minusMinutes(10)
        return fraudCheckRepository.findByStatusAndCreatedAtBefore(FraudCheckStatus.PROCESSING, cutoff)
    }

    @Transactional
    fun retryStuckCheck(paymentId: UUID) {
        val fraudCheck = fraudCheckRepository.findByPaymentId(paymentId)
            ?: throw IllegalArgumentException("Fraud check not found: $paymentId")

        if (fraudCheck.status == FraudCheckStatus.PROCESSING) {
            logger.warn("Retrying stuck fraud check: $paymentId")
            fraudCheck.markAsFailed("Stuck check - manual retry")
            fraudCheckRepository.save(fraudCheck)

            // This will trigger a retry in the next fraud check request
        }
    }

    private fun publishResultEvent(fraudCheck: FraudCheck) {
        val event = FraudCheckCompletedEvent(
            paymentId = fraudCheck.paymentId,
            approved = fraudCheck.isApproved,
            riskScore = fraudCheck.riskScore,
            riskLevel = fraudCheck.riskLevel.name,
            declineReason = fraudCheck.declineReason,
            triggeredRules = fraudCheck.triggeredRules?.split(",") ?: emptyList()
        )

        kafkaTemplate.send(
            MessageBuilder.withPayload(event)
                .setHeader("paymentId", fraudCheck.paymentId.toString())
                .setHeader("eventType", "FraudCheckCompletedEvent")
                .setHeader("correlationId", UUID.randomUUID().toString())
                .build()
        )
    }

    private fun publishFailureEvent(fraudCheck: FraudCheck, reason: String, canRetry: Boolean) {
        val event = FraudCheckFailedEvent(
            paymentId = fraudCheck.paymentId,
            reason = reason,
            canRetry = canRetry
        )

        kafkaTemplate.send(
            MessageBuilder.withPayload(event)
                .setHeader("paymentId", fraudCheck.paymentId.toString())
                .setHeader("eventType", "FraudCheckFailedEvent")
                .setHeader("correlationId", UUID.randomUUID().toString())
                .build()
        )
    }

    private fun publishManualReviewEvent(fraudCheck: FraudCheck, triggeredRules: List<String>) {
        val event = ManualReviewRequiredEvent(
            paymentId = fraudCheck.paymentId,
            riskScore = fraudCheck.riskScore,
            riskLevel = fraudCheck.riskLevel.name,
            triggeredRules = triggeredRules
        )

        kafkaTemplate.send(
            MessageBuilder.withPayload(event)
                .setHeader("paymentId", fraudCheck.paymentId.toString())
                .setHeader("eventType", "ManualReviewRequiredEvent")
                .setHeader("correlationId", UUID.randomUUID().toString())
                .build()
        )

        logger.info("Published manual review event for payment: ${fraudCheck.paymentId}")
    }
}