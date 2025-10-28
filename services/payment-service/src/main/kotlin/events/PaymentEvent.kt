package dev.jkiakumbo.paymentorchestrator.events

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

sealed class PaymentEvent {
    abstract val paymentId: UUID
    abstract val timestamp: LocalDateTime
}

data class PaymentCreatedEvent(
    override val paymentId: UUID,
    val amount: BigDecimal,
    val currency: String,
    val merchantId: String,
    val customerId: String,
    override val timestamp: LocalDateTime = LocalDateTime.now()
) : PaymentEvent()

data class FraudCheckRequestedEvent(
    override val paymentId: UUID,
    val amount: BigDecimal,
    val currency: String,
    val merchantId: String,
    val customerId: String,
    override val timestamp: LocalDateTime = LocalDateTime.now()
) : PaymentEvent()

data class FraudCheckCompletedEvent(
    override val paymentId: UUID,
    val approved: Boolean,
    val reason: String? = null,
    override val timestamp: LocalDateTime = LocalDateTime.now()
) : PaymentEvent()

data class FundsReservationRequestedEvent(
    override val paymentId: UUID,
    val amount: BigDecimal,
    val currency: String,
    val customerId: String,
    override val timestamp: LocalDateTime = LocalDateTime.now()
) : PaymentEvent()

data class FundsReservedEvent(
    override val paymentId: UUID,
    val reservationId: String,
    override val timestamp: LocalDateTime = LocalDateTime.now()
) : PaymentEvent()

data class FundsReservationFailedEvent(
    override val paymentId: UUID,
    val reason: String,
    override val timestamp: LocalDateTime = LocalDateTime.now()
) : PaymentEvent()

data class PaymentExecutionRequestedEvent(
    override val paymentId: UUID,
    val amount: BigDecimal,
    val currency: String,
    val merchantId: String,
    val customerId: String,
    override val timestamp: LocalDateTime = LocalDateTime.now()
) : PaymentEvent()

data class PaymentExecutedEvent(
    override val paymentId: UUID,
    val transactionId: String,
    override val timestamp: LocalDateTime = LocalDateTime.now()
) : PaymentEvent()

data class PaymentExecutionFailedEvent(
    override val paymentId: UUID,
    val reason: String,
    override val timestamp: LocalDateTime = LocalDateTime.now()
) : PaymentEvent()

data class LedgerUpdateRequestedEvent(
    override val paymentId: UUID,
    val amount: BigDecimal,
    val currency: String,
    val merchantId: String,
    val transactionId: String,
    override val timestamp: LocalDateTime = LocalDateTime.now()
) : PaymentEvent()

data class LedgerUpdatedEvent(
    override val paymentId: UUID,
    val ledgerEntryId: String,
    override val timestamp: LocalDateTime = LocalDateTime.now()
) : PaymentEvent()

data class LedgerUpdateFailedEvent(
    override val paymentId: UUID,
    val reason: String,
    override val timestamp: LocalDateTime = LocalDateTime.now()
) : PaymentEvent()

data class CompensationTriggeredEvent(
    override val paymentId: UUID,
    val failedStep: String,
    val reason: String,
    override val timestamp: LocalDateTime = LocalDateTime.now()
) : PaymentEvent()

data class CompensationCompletedEvent(
    override val paymentId: UUID,
    override val timestamp: LocalDateTime = LocalDateTime.now()
) : PaymentEvent()