package dev.jkiakumbo.paymentorchestrator.orchestratorservice.events

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

// Main payment events
data class PaymentCreatedEvent(
    val paymentId: UUID,
    val amount: BigDecimal,
    val currency: String,
    val merchantId: String,
    val customerId: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

data class PaymentCompletedEvent(
    val paymentId: UUID,
    val transactionId: String,
    val ledgerEntryId: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

data class PaymentFailedEvent(
    val paymentId: UUID,
    val reason: String,
    val failedStep: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

data class PaymentCompensatedEvent(
    val paymentId: UUID,
    val reason: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
)