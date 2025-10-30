package dev.jkiakumbo.paymentorchestrator.fraudservice.controller.dto

import dev.jkiakumbo.paymentorchestrator.fraudservice.events.FraudCheckRequestedEvent
import java.math.BigDecimal
import java.util.UUID


data class FraudCheckRequest(
    val paymentId: UUID,
    val amount: BigDecimal,
    val currency: String,
    val merchantId: String,
    val customerId: String
) {
    fun toEvent(): FraudCheckRequestedEvent {
        return FraudCheckRequestedEvent(
            paymentId = paymentId,
            amount = amount,
            currency = currency,
            merchantId = merchantId,
            customerId = customerId
        )
    }
}

data class FraudCheckResponse(
    val paymentId: UUID,
    val status: String,
    val message: String
)

data class FraudCheckStatusResponse(
    val paymentId: UUID,
    val status: String,
    val riskScore: Int,
    val riskLevel: String,
    val approved: Boolean
)