package dev.jkiakumbo.paymentorchestrator.fundsservice.scheduler

import dev.jkiakumbo.paymentorchestrator.fundsservice.service.FundsService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ExpiredReservationScheduler(
    private val fundsService: FundsService
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRate = 60000) // Run every minute
    fun cleanupExpiredReservations() {
        logger.debug("Checking for expired reservations...")
        fundsService.autoReleaseExpiredReservations()
    }
}