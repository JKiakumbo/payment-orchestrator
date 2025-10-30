package dev.jkiakumbo.paymentorchestrator.fraudservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import dev.jkiakumbo.paymentorchestrator.fraudservice.domain.FraudRuleRepository
import dev.jkiakumbo.paymentorchestrator.fraudservice.domain.RuleConfig
import dev.jkiakumbo.paymentorchestrator.fraudservice.events.FraudCheckRequestedEvent
import dev.jkiakumbo.paymentorchestrator.fraudservice.exception.RuleEvaluationException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Component
class FraudRuleEngine(
    private val fraudRuleRepository: FraudRuleRepository,
    private val objectMapper: ObjectMapper,
    private val riskAssessmentService: RiskAssessmentService
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    // Cache for active rules to avoid DB hits
    private val activeRulesCache: Cache<String, List<RuleConfig>> = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(5))
        .maximumSize(100)
        .build()

    // Cache for customer transaction counts (in-memory for demo, use Redis in production)
    private val customerTransactionCounts = ConcurrentHashMap<String, Int>()
    private val customerTransactionAmounts = ConcurrentHashMap<String, BigDecimal>()

    fun evaluateRules(event: FraudCheckRequestedEvent): RuleEvaluationResult {
        logger.debug("Evaluating fraud rules for payment: ${event.paymentId}")

        val startTime = System.currentTimeMillis()
        val triggeredRules = mutableListOf<String>()
        var totalRiskScore = 0

        try {
            val activeRules = getActiveRules()

            for (rule in activeRules) {
                try {
                    if (evaluateRule(rule, event)) {
                        triggeredRules.add(rule.id)
                        totalRiskScore += rule.riskScore
                        logger.debug("Rule triggered: ${rule.name} for payment: ${event.paymentId}")
                    }
                } catch (e: Exception) {
                    logger.error("Failed to evaluate rule: ${rule.id} for payment: ${event.paymentId}", e)
                    throw RuleEvaluationException(rule.id, e.message ?: "Unknown error")
                }
            }

            // Update velocity data
            updateVelocityData(event.customerId, event.amount)

            val evaluationTime = System.currentTimeMillis() - startTime
            logger.info("Rule evaluation completed for payment: ${event.paymentId}, " +
                    "triggered ${triggeredRules.size} rules, risk score: $totalRiskScore, " +
                    "time: ${evaluationTime}ms")

            return RuleEvaluationResult(
                triggeredRules = triggeredRules,
                totalRiskScore = totalRiskScore,
                evaluationTimeMs = evaluationTime
            )
        } catch (e: Exception) {
            val evaluationTime = System.currentTimeMillis() - startTime
            logger.error("Rule evaluation failed for payment: ${event.paymentId}, time: ${evaluationTime}ms", e)
            throw e
        }
    }

    private fun getActiveRules(): List<RuleConfig> {
        return activeRulesCache.get("active_rules") {
            fraudRuleRepository.findByIsActive(true)
                .map { it.toRuleConfig() }
                .also { logger.debug("Loaded ${it.size} active rules from database") }
        }
    }

    private fun evaluateRule(rule: RuleConfig, event: FraudCheckRequestedEvent): Boolean {
        return when (rule.id) {
            "HIGH_AMOUNT" -> evaluateHighAmountRule(rule, event)
            "BLACKLISTED_MERCHANT" -> evaluateBlacklistedMerchantRule(rule, event)
            "SUSPICIOUS_CUSTOMER" -> evaluateSuspiciousCustomerRule(rule, event)
            "TRANSACTION_VELOCITY" -> evaluateTransactionVelocityRule(rule, event)
            "GEOGRAPHICAL_MISMATCH" -> evaluateGeographicalMismatchRule(rule, event)
            else -> evaluateCustomRule(rule, event)
        }
    }

    private fun evaluateHighAmountRule(rule: RuleConfig, event: FraudCheckRequestedEvent): Boolean {
        val threshold = getRuleThreshold(rule, "threshold", BigDecimal("5000"))
        return event.amount > threshold
    }

    private fun evaluateBlacklistedMerchantRule(rule: RuleConfig, event: FraudCheckRequestedEvent): Boolean {
        val blacklistedMerchants = getRuleList(rule, "blacklistedMerchants", listOf("BLACKLISTED_MERCHANT_1", "BLACKLISTED_MERCHANT_2"))
        return blacklistedMerchants.any { event.merchantId.contains(it, ignoreCase = true) }
    }

    private fun evaluateSuspiciousCustomerRule(rule: RuleConfig, event: FraudCheckRequestedEvent): Boolean {
        val suspiciousCustomers = getRuleList(rule, "suspiciousCustomers", listOf("SUSPECT_", "RISKY_", "FRAUD_"))
        return suspiciousCustomers.any { event.customerId.startsWith(it, ignoreCase = true) }
    }

    private fun evaluateTransactionVelocityRule(rule: RuleConfig, event: FraudCheckRequestedEvent): Boolean {
        val maxTransactions = getRuleThreshold(rule, "maxTransactions", BigDecimal.TEN).toInt()
        val maxAmount = getRuleThreshold(rule, "maxAmount", BigDecimal("50000"))
        val timeWindowMinutes = getRuleThreshold(rule, "timeWindowMinutes", BigDecimal.valueOf(60)).toInt()

        val transactionCount = customerTransactionCounts.getOrDefault(event.customerId, 0)
        val transactionAmount = customerTransactionAmounts.getOrDefault(event.customerId, BigDecimal.ZERO)

        return transactionCount >= maxTransactions || transactionAmount >= maxAmount
    }

    private fun evaluateGeographicalMismatchRule(rule: RuleConfig, event: FraudCheckRequestedEvent): Boolean {
        // In a real implementation, this would check IP location vs billing address
        // For demo, we'll use a simple rule based on merchant ID
        val suspiciousLocations = getRuleList(rule, "suspiciousLocations", listOf("HIGH_RISK_COUNTRY"))
        return suspiciousLocations.any { event.merchantId.contains(it, ignoreCase = true) }
    }

    private fun evaluateCustomRule(rule: RuleConfig, event: FraudCheckRequestedEvent): Boolean {
        // For custom rules, you could implement a scripting engine or rule DSL
        // For now, we'll return false for unknown rules
        logger.warn("Unknown rule type: ${rule.id}")
        return false
    }

    private fun getRuleThreshold(rule: RuleConfig, key: String, default: BigDecimal): BigDecimal {
        return try {
            val config = rule.config?.let { objectMapper.readTree(it) }
            config?.get(key)?.decimalValue() ?: default
        } catch (e: Exception) {
            logger.warn("Failed to parse threshold for rule: ${rule.id}, key: $key", e)
            default
        }
    }

    private fun getRuleList(rule: RuleConfig, key: String, default: List<String>): List<String> {
        return try {
            val config = rule.config?.let { objectMapper.readTree(it) }
            config?.get(key)?.let {
                objectMapper.convertValue(it, Array<String>::class.java).toList()
            } ?: default
        } catch (e: Exception) {
            logger.warn("Failed to parse list for rule: ${rule.id}, key: $key", e)
            default
        }
    }

    private fun updateVelocityData(customerId: String, amount: BigDecimal) {
        customerTransactionCounts.merge(customerId, 1, Int::plus)
        customerTransactionAmounts.merge(customerId, amount, BigDecimal::add)

        // In production, this would be stored in Redis with TTL
    }

    fun refreshRulesCache() {
        activeRulesCache.invalidate("active_rules")
        logger.info("Refreshed fraud rules cache")
    }

    data class RuleEvaluationResult(
        val triggeredRules: List<String>,
        val totalRiskScore: Int,
        val evaluationTimeMs: Long
    )
}