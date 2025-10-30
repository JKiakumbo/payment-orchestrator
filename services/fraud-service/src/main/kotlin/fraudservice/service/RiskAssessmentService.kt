package dev.jkiakumbo.paymentorchestrator.fraudservice.service

import dev.jkiakumbo.paymentorchestrator.fraudservice.domain.RiskLevel
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class RiskAssessmentService {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun assessRisk(riskScore: Int): RiskAssessment {
        val riskLevel = calculateRiskLevel(riskScore)
        val isApproved = determineApproval(riskScore, riskLevel)

        return RiskAssessment(
            riskScore = riskScore,
            riskLevel = riskLevel,
            isApproved = isApproved
        )
    }

    private fun calculateRiskLevel(riskScore: Int): RiskLevel {
        return when {
            riskScore >= 70 -> RiskLevel.HIGH
            riskScore >= 30 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
    }

    private fun determineApproval(riskScore: Int, riskLevel: RiskLevel): Boolean {
        // Business logic for approval
        return when (riskLevel) {
            RiskLevel.LOW -> true    // Auto-approve low risk
            RiskLevel.MEDIUM -> riskScore < 50  // Approve medium risk below 50
            RiskLevel.HIGH -> false  // Always reject high risk
        }
    }

    fun shouldRequireManualReview(riskScore: Int, riskLevel: RiskLevel): Boolean {
        // Require manual review for medium-high risk transactions
        return riskLevel == RiskLevel.MEDIUM && riskScore >= 40
    }

    data class RiskAssessment(
        val riskScore: Int,
        val riskLevel: RiskLevel,
        val isApproved: Boolean
    )
}