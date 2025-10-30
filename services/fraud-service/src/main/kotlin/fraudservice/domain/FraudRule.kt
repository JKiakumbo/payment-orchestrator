package dev.jkiakumbo.paymentorchestrator.fraudservice.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "fraud_rules")
data class FraudRule(
    @Id
    val id: String, // Rule identifier like "HIGH_AMOUNT", "BLACKLISTED_MERCHANT"

    @Column(nullable = false)
    var name: String,

    @Column(name = "description", length = 1000)
    var description: String,

    @Column(name = "rule_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    var ruleType: RuleType,

    @Column(name = "risk_score", nullable = false)
    var riskScore: Int, // Points added when rule matches

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "config_json", length = 2000)
    var configJson: String? = null, // JSON configuration for the rule

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    fun toRuleConfig(): RuleConfig {
        return RuleConfig(
            id = this.id,
            name = this.name,
            riskScore = this.riskScore,
            config = this.configJson
        )
    }
}

enum class RuleType {
    AMOUNT_BASED,
    VELOCITY_BASED,
    GEOGRAPHICAL,
    BEHAVIORAL,
    BLACKLIST,
    WHITELIST,
    CUSTOM
}

data class RuleConfig(
    val id: String,
    val name: String,
    val riskScore: Int,
    val config: String?
)

interface FraudCheckRepository : org.springframework.data.repository.CrudRepository<FraudCheck, UUID> {
    fun findByPaymentId(paymentId: UUID): FraudCheck?
    fun findByCustomerId(customerId: String): List<FraudCheck>
    fun findByMerchantId(merchantId: String): List<FraudCheck>
    fun findByStatusAndCreatedAtBefore(status: FraudCheckStatus, cutoff: LocalDateTime): List<FraudCheck>

    @org.springframework.data.jpa.repository.Query(
        "SELECT COUNT(fc) FROM FraudCheck fc WHERE fc.customerId = :customerId AND fc.createdAt >= :since"
    )
    fun countByCustomerIdAndCreatedAtAfter(customerId: String, since: LocalDateTime): Long

    @org.springframework.data.jpa.repository.Query(
        "SELECT SUM(fc.amount) FROM FraudCheck fc WHERE fc.customerId = :customerId AND fc.createdAt >= :since"
    )
    fun sumAmountByCustomerIdAndCreatedAtAfter(customerId: String, since: LocalDateTime): java.math.BigDecimal?
}

interface FraudRuleRepository : org.springframework.data.repository.CrudRepository<FraudRule, String> {
    fun findByIsActive(isActive: Boolean): List<FraudRule>
}