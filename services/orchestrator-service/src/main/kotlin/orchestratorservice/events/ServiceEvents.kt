package dev.jkiakumbo.paymentorchestrator.orchestratorservice.events

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

// Fraud Service Events
data class FraudCheckRequestedEvent(
    val paymentId: UUID,
    val amount: BigDecimal,
    val currency: String,
    val merchantId: String,
    val customerId: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

data class FraudCheckCompletedEvent(
    val paymentId: UUID,
    val approved: Boolean,
    val riskScore: Int = 0,
    val riskLevel: String = "LOW",
    val declineReason: String? = null,
    val triggeredRules: List<String> = emptyList(),
    val timestamp: LocalDateTime = LocalDateTime.now()
)

// Funds Service Events
data class FundsReservationRequestedEvent(
    val paymentId: UUID,
    val amount: BigDecimal,
    val currency: String,
    val customerId: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

data class FundsReservedEvent(
    val paymentId: UUID,
    val reservationId: String,
    val expiresAt: LocalDateTime,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

data class FundsReservationFailedEvent(
    val paymentId: UUID,
    val reason: String,
    val canRetry: Boolean = true,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

data class FundsReleasedEvent(
    val paymentId: UUID,
    val reservationId: String,
    val reason: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

// Processor Service Events
data class PaymentExecutionRequestedEvent(
    val paymentId: UUID,
    val amount: BigDecimal,
    val currency: String,
    val merchantId: String,
    val customerId: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

data class PaymentExecutedEvent(
    val paymentId: UUID,
    val transactionId: String,
    val processorReference: String? = null,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

data class PaymentExecutionFailedEvent(
    val paymentId: UUID,
    val reason: String,
    val canRetry: Boolean = true,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

// Ledger Service Events
data class LedgerUpdateRequestedEvent(
    val paymentId: UUID,
    val amount: BigDecimal,
    val currency: String,
    val merchantId: String,
    val transactionId: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

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