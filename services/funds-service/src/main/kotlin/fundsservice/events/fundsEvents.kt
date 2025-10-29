package dev.jkiakumbo.paymentorchestrator.fundsservice.events

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID


data class FundsReservationRequestedEvent(
    val paymentId: UUID,
    val amount: BigDecimal,
    val currency: String,
    val customerId: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
)


data class FundsReservedEvent(
    val paymentId: UUID,
    val reservationId: String,
    val expiresAt: LocalDateTime,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

data class FundsReservationFailedEvent(
    val paymentId: UUID,
    val reason: String,
    val canRetry: Boolean = true,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

data class FundsReleasedEvent(
    val paymentId: UUID,
    val reservationId: String,
    val reason: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

data class FundsCommittedEvent(
    val paymentId: UUID,
    val reservationId: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

data class ReservationExpiredEvent(
    val paymentId: UUID,
    val reservationId: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
)