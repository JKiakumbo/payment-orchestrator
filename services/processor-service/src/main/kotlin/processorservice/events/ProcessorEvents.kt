package dev.jkiakumbo.paymentorchestrator.processorservice.events

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

// Incoming Events (from payment-orchestrator)
data class PaymentExecutionRequestedEvent(
    val paymentId: UUID,
    val amount: BigDecimal,
    val currency: String,
    val merchantId: String,
    val customerId: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

// Outgoing Events (to payment-orchestrator)
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

data class PaymentExecutionRetryEvent(
    val paymentId: UUID,
    val retryCount: Int,
    val reason: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
)