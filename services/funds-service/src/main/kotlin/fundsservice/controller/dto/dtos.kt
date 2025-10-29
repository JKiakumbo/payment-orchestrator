package dev.jkiakumbo.paymentorchestrator.fundsservice.controller.dto

import java.math.BigDecimal

data class AccountBalanceResponse(
    val customerId: String,
    val availableBalance: BigDecimal,
    val totalReserved: BigDecimal,
    val effectiveBalance: BigDecimal
)

data class UpdateOverdraftRequest(
    val maxOverdraft: BigDecimal
)