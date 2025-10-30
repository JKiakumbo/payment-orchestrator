package dev.jkiakumbo.paymentorchestrator.ledgerservice.events

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

// Incoming Events (from payment-orchestrator)
data class LedgerUpdateRequestedEvent(
    val paymentId: UUID,
    val amount: BigDecimal,
    val currency: String,
    val merchantId: String,
    val transactionId: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

// Outgoing Events (to payment-orchestrator)
data class LedgerUpdatedEvent(
    val paymentId: UUID,
    val ledgerEntryId: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

data class LedgerUpdateFailedEvent(
    val paymentId: UUID,
    val reason: String,
    val canRetry: Boolean = true,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

data class LedgerReversedEvent(
    val paymentId: UUID,
    val originalEntryId: String,
    val reversalEntryId: String,
    val reason: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
)