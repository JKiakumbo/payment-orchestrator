package dev.jkiakumbo.paymentorchestrator.fraudservice.config

import com.fasterxml.jackson.databind.ObjectMapper
import dev.jkiakumbo.paymentorchestrator.fraudservice.domain.FraudRule
import dev.jkiakumbo.paymentorchestrator.fraudservice.domain.FraudRuleRepository
import dev.jkiakumbo.paymentorchestrator.fraudservice.domain.RuleType
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class FraudRulesConfig {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun initializeDefaultRules(
        fraudRuleRepository: FraudRuleRepository,
        objectMapper: ObjectMapper
    ): CommandLineRunner {
        return CommandLineRunner {
            logger.info("Initializing default fraud rules...")

            val defaultRules = listOf(
                FraudRule(
                    id = "HIGH_AMOUNT",
                    name = "High Amount Transaction",
                    description = "Flag transactions above threshold amount",
                    ruleType = RuleType.AMOUNT_BASED,
                    riskScore = 40,
                    configJson = objectMapper.writeValueAsString(
                        mapOf(
                            "threshold" to 5000.00
                        )
                    )
                ),
                FraudRule(
                    id = "BLACKLISTED_MERCHANT",
                    name = "Blacklisted Merchant",
                    description = "Flag transactions from blacklisted merchants",
                    ruleType = RuleType.BLACKLIST,
                    riskScore = 80,
                    configJson = objectMapper.writeValueAsString(mapOf(
                        "blacklistedMerchants" to listOf("BLACKLISTED_MERCHANT_1", "BLACKLISTED_MERCHANT_2")
                    ))
                ),
                FraudRule(
                    id = "SUSPICIOUS_CUSTOMER",
                    name = "Suspicious Customer",
                    description = "Flag transactions from suspicious customers",
                    ruleType = RuleType.BLACKLIST,
                    riskScore = 60,
                    configJson = objectMapper.writeValueAsString(mapOf(
                        "suspiciousCustomers" to listOf("SUSPECT_", "RISKY_", "FRAUD_")
                    ))
                ),
                FraudRule(
                    id = "TRANSACTION_VELOCITY",
                    name = "Transaction Velocity",
                    description = "Flag high-frequency or high-volume transactions",
                    ruleType = RuleType.VELOCITY_BASED,
                    riskScore = 30,
                    configJson = objectMapper.writeValueAsString(mapOf(
                        "maxTransactions" to 10,
                        "maxAmount" to 50000.00,
                        "timeWindowMinutes" to 60
                    ))
                ),
                FraudRule(
                    id = "GEOGRAPHICAL_MISMATCH",
                    name = "Geographical Mismatch",
                    description = "Flag transactions from high-risk locations",
                    ruleType = RuleType.GEOGRAPHICAL,
                    riskScore = 50,
                    configJson = objectMapper.writeValueAsString(mapOf(
                        "suspiciousLocations" to listOf("HIGH_RISK_COUNTRY")
                    ))
                )
            )

            defaultRules.forEach { rule ->
                if (!fraudRuleRepository.existsById(rule.id)) {
                    fraudRuleRepository.save(rule)
                    logger.info("Created default rule: ${rule.name}")
                }
            }

            logger.info("Default fraud rules initialization completed")
        }
    }
}