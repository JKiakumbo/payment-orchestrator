package dev.jkiakumbo.paymentorchestrator.orchestratorservice.controller

import dev.jkiakumbo.paymentorchestrator.orchestratorservice.controller.dto.PaymentRequest
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.controller.dto.PaymentResponse
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.controller.dto.toDto
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.service.OrchestrationService
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.service.RetryService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@RequestMapping("/api/payments")
class PaymentController(
    private val paymentOrchestrationService: OrchestrationService,
    private val retryService: RetryService
) {

    @PostMapping
    fun initiatePayment(@RequestBody request: PaymentRequest): ResponseEntity<PaymentResponse> {
        return try {
            val paymentId = paymentOrchestrationService.initiatePayment(
                amount = request.amount,
                currency = request.currency,
                merchantId = request.merchantId,
                customerId = request.customerId
            )

            val response = PaymentResponse(
                paymentId = paymentId,
                status = "INITIATED",
                message = "Payment processing initiated"
            )

            ResponseEntity.ok(response)
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(
                PaymentResponse(
                    paymentId = null,
                    status = "FAILED",
                    message = e.message ?: "Unknown error"
                )
            )
        }
    }

    @GetMapping("/{paymentId}")
    fun getPaymentStatus(@PathVariable paymentId: UUID): ResponseEntity<Any> {
        return try {
            val payment = paymentOrchestrationService.getPaymentStatus(paymentId)
            if (payment != null) {
                ResponseEntity.ok(payment)
            } else {
                ResponseEntity.notFound().build()
            }
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(
                mapOf("error" to (e.message ?: "Unknown error"))
            )
        }
    }

    @GetMapping("/merchant/{merchantId}")
    fun getPaymentsByMerchant(@PathVariable merchantId: String): ResponseEntity<List<Any>> {
        return try {
            val payments = paymentOrchestrationService.getPaymentsByMerchant(merchantId)
            ResponseEntity.ok(payments.map { it.toDto() })
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(emptyList())
        }
    }

    @GetMapping("/customer/{customerId}")
    fun getPaymentsByCustomer(@PathVariable customerId: String): ResponseEntity<List<Any>> {
        return try {
            val payments = paymentOrchestrationService.getPaymentsByCustomer(customerId)
            ResponseEntity.ok(payments.map { it.toDto() })
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(emptyList())
        }
    }

    @PostMapping("/{paymentId}/retry")
    fun retryPayment(@PathVariable paymentId: UUID): ResponseEntity<Map<String, String>> {
        return try {
            retryService.manualRetry(paymentId)
            ResponseEntity.ok(mapOf("message" to "Retry initiated for payment: $paymentId"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Unknown error")))
        }
    }

    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(mapOf("status" to "UP", "service" to "payment-orchestrator"))
    }
}
