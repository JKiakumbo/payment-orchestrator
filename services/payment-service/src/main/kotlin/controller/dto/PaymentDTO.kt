package dev.jkiakumbo.paymentorchestrator.controller.dto

import java.math.BigDecimal
import java.util.UUID

data class PaymentRequest(
    val amount: BigDecimal,
    val currency: String,
    val merchantId: String,
    val customerId: String
)

data class PaymentResponse(
    val paymentId: UUID,
    val status: String
)