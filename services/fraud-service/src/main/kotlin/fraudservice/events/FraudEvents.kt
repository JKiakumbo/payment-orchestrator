package dev.jkiakumbo.paymentorchestrator.fraudservice.events

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

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
    val reason: String? = null,
    val timestamp: LocalDateTime = LocalDateTime.now()
)