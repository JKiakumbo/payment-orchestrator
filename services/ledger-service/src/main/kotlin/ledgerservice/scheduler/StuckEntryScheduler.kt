package dev.jkiakumbo.paymentorchestrator.ledgerservice.scheduler

import dev.jkiakumbo.paymentorchestrator.ledgerservice.service.LedgerService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class StuckEntryScheduler(
    private val ledgerService: LedgerService
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRate = 300000) // Run every 5 minutes
    fun checkForStuckLedgerEntries() {
        logger.debug("Checking for stuck ledger entries...")

        val stuckEntries = ledgerService.findStuckEntries()
        if (stuckEntries.isNotEmpty()) {
            logger.warn("Found ${stuckEntries.size} stuck ledger entries")

            stuckEntries.forEach { entry ->
                try {
                    ledgerService.retryStuckEntry(entry.paymentId)
                    logger.info("Retried stuck ledger entry: ${entry.paymentId}")
                } catch (e: Exception) {
                    logger.error("Failed to retry stuck ledger entry: ${entry.paymentId}", e)
                }
            }
        }
    }
}