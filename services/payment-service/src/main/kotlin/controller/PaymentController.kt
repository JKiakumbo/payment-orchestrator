package dev.jkiakumbo.paymentorchestrator.controller


import dev.jkiakumbo.paymentorchestrator.controller.dto.PaymentRequest
import dev.jkiakumbo.paymentorchestrator.controller.dto.PaymentResponse
import dev.jkiakumbo.paymentorchestrator.service.PaymentService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/payments")
class PaymentController(
    private val paymentService: PaymentService
) {

    @PostMapping
    fun initiatePayment(@RequestBody request: PaymentRequest): ResponseEntity<PaymentResponse> {
        val paymentId = paymentService.initiatePayment(
            amount = request.amount,
            currency = request.currency,
            merchantId = request.merchantId,
            customerId = request.customerId
        )

        return ResponseEntity.ok(PaymentResponse(paymentId = paymentId, status = "INITIATED"))
    }

    @GetMapping("/{paymentId}")
    fun getPaymentStatus(@PathVariable paymentId: UUID): ResponseEntity<Any> {
        val payment = paymentService.getPaymentStatus(paymentId)

        return if (payment != null) {
            ResponseEntity.ok(payment)
        } else {
            ResponseEntity.notFound().build()
        }
    }
}
