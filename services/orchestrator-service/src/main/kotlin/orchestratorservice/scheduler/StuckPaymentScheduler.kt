package dev.jkiakumbo.paymentorchestrator.orchestratorservice.scheduler

import dev.jkiakumbo.paymentorchestrator.orchestratorservice.service.RetryService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class StuckPaymentScheduler(
    private val retryService: RetryService
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRate = 300000) // Run every 5 minutes
    fun checkForStuckPayments() {
        logger.debug("Checking for stuck payments...")

        val stuckPayments = retryService.findStuckPayments()
        if (stuckPayments.isNotEmpty()) {
            logger.warn("Found ${stuckPayments.size} stuck payments")

            stuckPayments.forEach { payment ->
                try {
                    logger.info("Attempting to retry stuck payment: ${payment.id}")
                    retryService.manualRetry(payment.id)
                } catch (e: Exception) {
                    logger.error("Failed to retry stuck payment: ${payment.id}", e)
                }
            }
        }
    }
}