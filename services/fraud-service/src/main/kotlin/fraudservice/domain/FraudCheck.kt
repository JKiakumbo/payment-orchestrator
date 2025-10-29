package dev.jkiakumbo.paymentorchestrator.fraudservice.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "fraud_checks",
    indexes = [
        Index(name = "idx_fraud_check_payment_id", columnList = "paymentId", unique = true),
        Index(name = "idx_fraud_check_customer_id", columnList = "customerId"),
        Index(name = "idx_fraud_check_merchant_id", columnList = "merchantId"),
        Index(name = "idx_fraud_check_status", columnList = "status"),
        Index(name = "idx_fraud_check_created_at", columnList = "createdAt")
    ]
)
data class FraudCheck(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "payment_id", nullable = false, unique = true)
    val paymentId: UUID,

    @Column(nullable = false, precision = 19, scale = 4)
    val amount: BigDecimal,

    @Column(nullable = false, length = 3)
    val currency: String,

    @Column(name = "merchant_id", nullable = false)
    val merchantId: String,

    @Column(name = "customer_id", nullable = false)
    val customerId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: FraudCheckStatus,

    @Column(name = "risk_score", nullable = false)
    var riskScore: Int = 0,

    @Column(name = "risk_level", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var riskLevel: RiskLevel = RiskLevel.LOW,

    @Column(name = "is_approved", nullable = false)
    var isApproved: Boolean = false,

    @Column(name = "decline_reason", length = 500)
    var declineReason: String? = null,

    @Column(name = "triggered_rules", length = 1000)
    var triggeredRules: String? = null, // JSON array of rule IDs

    @Column(name = "evaluation_time_ms", nullable = false)
    var evaluationTimeMs: Long = 0,

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,

    @Column(name = "last_retry_at")
    var lastRetryAt: LocalDateTime? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    fun markAsProcessing() {
        this.status = FraudCheckStatus.PROCESSING
        this.updatedAt = LocalDateTime.now()
    }

    fun markAsCompleted(approved: Boolean, riskScore: Int, riskLevel: RiskLevel, triggeredRules: List<String>? = null) {
        this.status = FraudCheckStatus.COMPLETED
        this.isApproved = approved
        this.riskScore = riskScore
        this.riskLevel = riskLevel
        this.triggeredRules = triggeredRules?.joinToString(",")
        this.updatedAt = LocalDateTime.now()

        if (!approved) {
            this.declineReason = buildDeclineReason(riskLevel, triggeredRules)
        }
    }

    fun markAsFailed(reason: String) {
        this.status = FraudCheckStatus.FAILED
        this.declineReason = reason
        this.updatedAt = LocalDateTime.now()
    }

    fun incrementRetry() {
        this.retryCount++
        this.lastRetryAt = LocalDateTime.now()
        this.updatedAt = LocalDateTime.now()
    }

    fun canRetry(maxRetries: Int = 3): Boolean {
        return this.status == FraudCheckStatus.FAILED &&
                this.retryCount < maxRetries &&
                (lastRetryAt == null ||
                        lastRetryAt!!.isBefore(LocalDateTime.now().minusMinutes(5)))
    }

    private fun buildDeclineReason(riskLevel: RiskLevel, triggeredRules: List<String>?): String {
        return when (riskLevel) {
            RiskLevel.HIGH -> "High risk transaction detected"
            RiskLevel.MEDIUM -> "Medium risk transaction requires manual review"
            RiskLevel.LOW -> "Low risk but below threshold"
        } + if (!triggeredRules.isNullOrEmpty()) {
            ". Triggered rules: ${triggeredRules.joinToString(", ")}"
        } else {
            ""
        }
    }
}

enum class FraudCheckStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}

enum class RiskLevel {
    LOW,      // 0-30
    MEDIUM,   // 31-70
    HIGH      // 71-100
}
