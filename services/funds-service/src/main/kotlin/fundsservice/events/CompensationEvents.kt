package dev.jkiakumbo.paymentorchestrator.fundsservice.events

import java.time.LocalDateTime
import java.util.UUID

data class CompensationRequestedEvent(
    val paymentId: UUID,
    val reason: String,
    val failedStep: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

data class CompensationCompletedEvent(
    val paymentId: UUID,
    val service: String = "funds-service",
    val timestamp: LocalDateTime = LocalDateTime.now()
)