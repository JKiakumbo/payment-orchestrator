package dev.jkiakumbo.paymentorchestrator.processorservice.events

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
    val service: String = "processor-service",
    val timestamp: LocalDateTime = LocalDateTime.now()
)