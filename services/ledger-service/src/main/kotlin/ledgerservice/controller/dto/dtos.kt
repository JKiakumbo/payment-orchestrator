package dev.jkiakumbo.paymentorchestrator.ledgerservice.controller.dto

import dev.jkiakumbo.paymentorchestrator.ledgerservice.events.LedgerUpdateRequestedEvent
import java.math.BigDecimal
import java.util.UUID

data class CreateLedgerEntryRequest(
    val paymentId: UUID,
    val amount: BigDecimal,
    val currency: String,
    val merchantId: String,
    val transactionId: String
) {
    fun toEvent(): LedgerUpdateRequestedEvent {
        return LedgerUpdateRequestedEvent(
            paymentId = paymentId,
            amount = amount,
            currency = currency,
            merchantId = merchantId,
            transactionId = transactionId
        )
    }
}

data class LedgerEntryResponse(
    val paymentId: UUID,
    val status: String,
    val message: String
)

data class LedgerEntryStatusResponse(
    val paymentId: UUID,
    val status: String,
    val ledgerEntryId: String
)

data class AccountBalanceResponse(
    val accountCode: String,
    val accountingPeriod: String,
    val balance: BigDecimal
)

data class ReverseLedgerEntryRequest(
    val reason: String
)