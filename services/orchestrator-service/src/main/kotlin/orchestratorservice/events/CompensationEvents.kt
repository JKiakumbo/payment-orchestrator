package dev.jkiakumbo.paymentorchestrator.orchestratorservice.events

import java.time.LocalDateTime
import java.util.*

data class CompensationTriggeredEvent(
    val paymentId: UUID,
    val failedStep: String,
    val reason: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

data class CompensationCompletedEvent(
    val paymentId: UUID,
    val service: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
)