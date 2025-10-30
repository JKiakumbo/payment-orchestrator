package dev.jkiakumbo.paymentorchestrator.fraudservice.controller

import dev.jkiakumbo.paymentorchestrator.fraudservice.controller.dto.FraudCheckRequest
import dev.jkiakumbo.paymentorchestrator.fraudservice.controller.dto.FraudCheckResponse
import dev.jkiakumbo.paymentorchestrator.fraudservice.controller.dto.FraudCheckStatusResponse
import dev.jkiakumbo.paymentorchestrator.fraudservice.service.FraudDetectionService
import dev.jkiakumbo.paymentorchestrator.fraudservice.service.FraudRuleEngine
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/fraud")
class FraudController(
    private val fraudDetectionService: FraudDetectionService,
    private val fraudRuleEngine: FraudRuleEngine
) {

    @PostMapping("/check")
    fun performFraudCheck(@RequestBody request: FraudCheckRequest): ResponseEntity<FraudCheckResponse> {
        return try {
            // Convert to event and process
            val event = request.toEvent()
            fraudDetectionService.processFraudCheck(event)

            val response = FraudCheckResponse(
                paymentId = request.paymentId,
                status = "PROCESSING",
                message = "Fraud check initiated"
            )

            ResponseEntity.ok(response)
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(
                FraudCheckResponse(
                    paymentId = request.paymentId,
                    status = "FAILED",
                    message = e.message ?: "Unknown error"
                )
            )
        }
    }

    @GetMapping("/checks/{paymentId}")
    fun getFraudCheckStatus(@PathVariable paymentId: UUID): ResponseEntity<FraudCheckStatusResponse> {
        // This would typically query the database for the fraud check status
        // For now, we'll return a mock response
        return ResponseEntity.ok(
            FraudCheckStatusResponse(
                paymentId = paymentId,
                status = "COMPLETED",
                riskScore = 25,
                riskLevel = "LOW",
                approved = true
            )
        )
    }

    @PostMapping("/rules/refresh")
    fun refreshFraudRules(): ResponseEntity<Map<String, String>> {
        fraudRuleEngine.refreshRulesCache()
        return ResponseEntity.ok(mapOf("message" to "Fraud rules cache refreshed"))
    }

    @PostMapping("/stuck-checks/retry/{paymentId}")
    fun retryStuckCheck(@PathVariable paymentId: UUID): ResponseEntity<Map<String, String>> {
        return try {
            fraudDetectionService.retryStuckCheck(paymentId)
            ResponseEntity.ok(mapOf("message" to "Retry initiated for stuck check: $paymentId"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Unknown error")))
        }
    }
}
