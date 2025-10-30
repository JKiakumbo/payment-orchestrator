package dev.jkiakumbo.paymentorchestrator.orchestratorservice.controller.dto
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.domain.Payment
import java.math.BigDecimal
import java.util.UUID

data class PaymentRequest(
    val amount: BigDecimal,
    val currency: String,
    val merchantId: String,
    val customerId: String
)

data class PaymentResponse(
    val paymentId: UUID?,
    val status: String,
    val message: String
)

data class PaymentStatusResponse(
    val paymentId: UUID,
    val status: String,
    val currentStep: String?,
    val failureReason: String?,
    val createdAt: String,
    val updatedAt: String
)

fun Payment.toDto(): Map<String, Any?> {
    return mapOf(
        "id" to id,
        "amount" to amount,
        "currency" to currency,
        "merchantId" to merchantId,
        "customerId" to customerId,
        "state" to state,
        "currentStep" to currentStep,
        "transactionId" to transactionId,
        "failureReason" to failureReason,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )
}
