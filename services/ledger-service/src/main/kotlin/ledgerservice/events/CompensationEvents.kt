package dev.jkiakumbo.paymentorchestrator.ledgerservice.events

import java.time.LocalDateTime
import java.util.*

data class CompensationRequestedEvent(
    val paymentId: UUID,
    val reason: String,
    val failedStep: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

data class CompensationCompletedEvent(
    val paymentId: UUID,
    val service: String = "ledger-service",
    val timestamp: LocalDateTime = LocalDateTime.now()
)