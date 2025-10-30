package dev.jkiakumbo.paymentorchestrator.fraudservice.events

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

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

data class FraudCheckFailedEvent(
    val paymentId: UUID,
    val reason: String,
    val canRetry: Boolean = true,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

data class ManualReviewRequiredEvent(
    val paymentId: UUID,
    val riskScore: Int,
    val riskLevel: String,
    val triggeredRules: List<String>,
    val timestamp: LocalDateTime = LocalDateTime.now()
)