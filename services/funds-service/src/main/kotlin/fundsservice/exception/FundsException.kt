package dev.jkiakumbo.paymentorchestrator.fundsservice.exception

import java.math.BigDecimal

class InsufficientFundsException(message: String) : RuntimeException(message) {
    constructor(available: BigDecimal, requested: BigDecimal) :
            this("Insufficient funds. Available: $available, Requested: $requested")
}

class AccountNotFoundException(customerId: String) :
    RuntimeException("Account not found for customer: $customerId")

class CurrencyMismatchException(message: String) : RuntimeException(message)

class ReservationNotFoundException(paymentId: String) :
    RuntimeException("Reservation not found for payment: $paymentId")

class ReservationExpiredException(paymentId: String) :
    RuntimeException("Reservation expired for payment: $paymentId")