package dev.jkiakumbo.paymentorchestrator.fraudservice.scheduler

import dev.jkiakumbo.paymentorchestrator.fraudservice.service.FraudDetectionService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class StuckCheckScheduler(
    private val fraudDetectionService: FraudDetectionService
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRate = 300000) // Run every 5 minutes
    fun checkForStuckFraudChecks() {
        logger.debug("Checking for stuck fraud checks...")

        val stuckChecks = fraudDetectionService.findStuckChecks()
        if (stuckChecks.isNotEmpty()) {
            logger.warn("Found ${stuckChecks.size} stuck fraud checks")

            stuckChecks.forEach { check ->
                try {
                    fraudDetectionService.retryStuckCheck(check.paymentId)
                    logger.info("Retried stuck fraud check: ${check.paymentId}")
                } catch (e: Exception) {
                    logger.error("Failed to retry stuck fraud check: ${check.paymentId}", e)
                }
            }
        }
    }
}