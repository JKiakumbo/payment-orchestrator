package dev.jkiakumbo.paymentorchestrator.fundsservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import dev.jkiakumbo.paymentorchestrator.fundsservice.domain.FundReservation
import dev.jkiakumbo.paymentorchestrator.fundsservice.domain.ReservationStatus
import dev.jkiakumbo.paymentorchestrator.fundsservice.events.FundsCommittedEvent
import dev.jkiakumbo.paymentorchestrator.fundsservice.events.FundsReleasedEvent
import dev.jkiakumbo.paymentorchestrator.fundsservice.events.FundsReservationFailedEvent
import dev.jkiakumbo.paymentorchestrator.fundsservice.events.FundsReservationRequestedEvent
import dev.jkiakumbo.paymentorchestrator.fundsservice.events.FundsReservedEvent
import dev.jkiakumbo.paymentorchestrator.fundsservice.events.ReservationExpiredEvent
import dev.jkiakumbo.paymentorchestrator.fundsservice.exception.CurrencyMismatchException
import dev.jkiakumbo.paymentorchestrator.fundsservice.exception.InsufficientFundsException
import dev.jkiakumbo.paymentorchestrator.fundsservice.exception.ReservationNotFoundException
import dev.jkiakumbo.paymentorchestrator.fundsservice.repositories.FundReservationRepository
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.support.MessageBuilder
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
class FundsService(
    private val reservationRepository: FundReservationRepository,
    private val accountService: AccountService,
    private val objectMapper: ObjectMapper,
    private val kafkaTemplate: KafkaTemplate<String, String>
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val reservationTimeoutMinutes = 30L

    @Transactional
    fun reserveFunds(event: FundsReservationRequestedEvent) {
        logger.info("Reserving funds for payment: ${event.paymentId}, customer: ${event.customerId}, amount: ${event.amount}")

        // Check idempotency - if we already processed this payment
        val existingReservation = reservationRepository.findByPaymentId(event.paymentId)
        if (existingReservation != null) {
            handleExistingReservation(existingReservation, event)
            return
        }

        // Create new reservation
        val reservation = FundReservation.create(
            paymentId = event.paymentId,
            customerId = event.customerId,
            amount = event.amount,
            currency = event.currency,
            reservationTimeoutMinutes = reservationTimeoutMinutes
        )

        reservationRepository.save(reservation)

        // Process the reservation
        processReservation(reservation)
    }

    private fun handleExistingReservation(reservation: FundReservation, event: FundsReservationRequestedEvent) {
        logger.info("Funds reservation already exists for payment: ${event.paymentId}, status: ${reservation.status}")

        when (reservation.status) {
            ReservationStatus.RESERVED -> {
                // Idempotency - already succeeded, publish success event
                publishSuccessEvent(reservation)
            }
            ReservationStatus.FAILED -> {
                if (reservation.canRetry()) {
                    logger.info("Retrying failed reservation: ${event.paymentId}, retry count: ${reservation.retryCount}")
                    reservation.incrementRetry()
                    reservation.status = ReservationStatus.PENDING
                    reservationRepository.save(reservation)

                    // Retry processing
                    processReservation(reservation)
                } else {
                    logger.warn("Reservation ${event.paymentId} has exceeded maximum retry attempts")
                    // Do nothing - already failed and cannot retry
                }
            }
            ReservationStatus.EXPIRED -> {
                logger.info("Reservation ${event.paymentId} has expired, creating new reservation")
                // Create new reservation with new expiry
                val newReservation = FundReservation.create(
                    paymentId = event.paymentId,
                    customerId = event.customerId,
                    amount = event.amount,
                    currency = event.currency,
                    reservationTimeoutMinutes = reservationTimeoutMinutes
                )
                reservationRepository.save(newReservation)
                processReservation(newReservation)
            }
            else -> {
                // For other statuses, continue processing
                processReservation(reservation)
            }
        }
    }

    @Retryable(
        retryFor = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 1000, multiplier = 2.0)
    )
    @Transactional
    fun processReservation(reservation: FundReservation) {
        try {
            // Get account with pessimistic lock to prevent concurrent modifications
//            val account = accountService.getAccountWithLock(reservation.customerId)
            val account = accountService.getOrCreateAccount(reservation.customerId, reservation.currency)

            // Validate currency
            accountService.validateCurrency(account, reservation.currency)

            // Check if reservation is expired
            if (reservation.isExpired()) {
                reservation.markAsExpired()
                reservationRepository.save(reservation)
                publishExpiredEvent(reservation)
                return
            }

            // Reserve funds
            account.reserveFunds(reservation.amount)
            accountService.getOrCreateAccount(account.customerId, account.currency) // This ensures account is saved

            // Mark reservation as reserved
            reservation.markAsReserved()
            reservationRepository.save(reservation)

            publishSuccessEvent(reservation)
            logger.info("Funds reserved successfully for payment: ${reservation.paymentId}")

        } catch (e: Exception) {
            logger.error("Funds reservation failed for payment: ${reservation.paymentId}", e)

            reservation.markAsFailed(e.message ?: "Unknown error")
            reservationRepository.save(reservation)

            val canRetry = e !is InsufficientFundsException &&
                    e !is CurrencyMismatchException &&
                    reservation.canRetry()

            publishFailureEvent(reservation, e.message ?: "Unknown error", canRetry)
        }
    }

    @Transactional
    fun releaseFunds(paymentId: UUID, reason: String = "Compensation") {
        logger.info("Releasing funds for payment: $paymentId, reason: $reason")

        val reservation = reservationRepository.findByPaymentId(paymentId)
            ?: throw ReservationNotFoundException(paymentId.toString())

        if (reservation.status == ReservationStatus.RESERVED) {
            val account = accountService.getAccountWithLock(reservation.customerId)
            account.releaseFunds(reservation.amount)
            accountService.getOrCreateAccount(account.customerId, account.currency)

            reservation.markAsReleased()
            reservationRepository.save(reservation)

            publishReleasedEvent(reservation, reason)
            logger.info("Funds released for payment: $paymentId")
        } else {
            logger.warn("Cannot release funds for payment: $paymentId, status: ${reservation.status}")
        }
    }

    @Transactional
    fun commitFunds(paymentId: UUID) {
        logger.info("Committing funds for payment: $paymentId")

        val reservation = reservationRepository.findByPaymentId(paymentId)
            ?: throw ReservationNotFoundException(paymentId.toString())

        if (reservation.status == ReservationStatus.RESERVED) {
            val account = accountService.getAccountWithLock(reservation.customerId)
            account.commitFunds(reservation.amount)
            accountService.getOrCreateAccount(account.customerId, account.currency)

            reservation.markAsCommitted()
            reservationRepository.save(reservation)

            publishCommittedEvent(reservation)
            logger.info("Funds committed for payment: $paymentId")
        } else {
            logger.warn("Cannot commit funds for payment: $paymentId, status: ${reservation.status}")
        }
    }

    @Transactional
    fun findExpiredReservations(): List<FundReservation> {
        val cutoff = LocalDateTime.now()
        return reservationRepository.findByStatusAndExpiresAtBefore(ReservationStatus.RESERVED, cutoff)
    }

    @Transactional
    fun autoReleaseExpiredReservations() {
        val expiredReservations = findExpiredReservations()

        expiredReservations.forEach { reservation ->
            try {
                logger.info("Auto-releasing expired reservation: ${reservation.paymentId}")
                releaseFunds(reservation.paymentId, "Reservation expired")
            } catch (e: Exception) {
                logger.error("Failed to auto-release expired reservation: ${reservation.paymentId}", e)
            }
        }

        if (expiredReservations.isNotEmpty()) {
            logger.info("Auto-released ${expiredReservations.size} expired reservations")
        }
    }

    private fun publishSuccessEvent(reservation: FundReservation) {
        val event = FundsReservedEvent(
            paymentId = reservation.paymentId,
            reservationId = reservation.id.toString(),
            expiresAt = reservation.expiresAt
        )

        kafkaTemplate.send(
            MessageBuilder.withPayload(objectMapper.writeValueAsString(event))
                .setHeader(KafkaHeaders.TOPIC, "funds-reservation-results")
                .setHeader("paymentId", reservation.paymentId.toString())
                .setHeader("eventType", "FundsReservedEvent")
                .setHeader("correlationId", UUID.randomUUID().toString())
                .build()
        )
    }

    private fun publishFailureEvent(reservation: FundReservation, reason: String, canRetry: Boolean) {
        val event = FundsReservationFailedEvent(
            paymentId = reservation.paymentId,
            reason = reason,
            canRetry = canRetry
        )

        kafkaTemplate.send(
            MessageBuilder.withPayload(objectMapper.writeValueAsString(event))
                .setHeader(KafkaHeaders.TOPIC, "funds-reservation-results")
                .setHeader("paymentId", reservation.paymentId.toString())
                .setHeader("eventType", "FundsReservationFailedEvent")
                .setHeader("correlationId", UUID.randomUUID().toString())
                .build()
        )
    }

    private fun publishReleasedEvent(reservation: FundReservation, reason: String) {
        val event = FundsReleasedEvent(
            paymentId = reservation.paymentId,
            reservationId = reservation.id.toString(),
            reason = reason
        )

        kafkaTemplate.send(
            MessageBuilder.withPayload(objectMapper.writeValueAsString(event))
                .setHeader(KafkaHeaders.TOPIC, "compensation-requests")
                .setHeader("paymentId", reservation.paymentId.toString())
                .setHeader("eventType", "FundsReleasedEvent")
                .setHeader("correlationId", UUID.randomUUID().toString())
                .build()
        )
    }

    private fun publishCommittedEvent(reservation: FundReservation) {
        val event = FundsCommittedEvent(
            paymentId = reservation.paymentId,
            reservationId = reservation.id.toString()
        )

        kafkaTemplate.send(
            MessageBuilder.withPayload(objectMapper.writeValueAsString(event))
                .setHeader(KafkaHeaders.TOPIC, "funds-commit-requests")
                .setHeader("paymentId", reservation.paymentId.toString())
                .setHeader("eventType", "FundsCommittedEvent")
                .setHeader("correlationId", UUID.randomUUID().toString())
                .build()
        )
    }

    private fun publishExpiredEvent(reservation: FundReservation) {
        val event = ReservationExpiredEvent(
            paymentId = reservation.paymentId,
            reservationId = reservation.id.toString()
        )

        kafkaTemplate.send(
            MessageBuilder.withPayload(objectMapper.writeValueAsString(event))
                .setHeader(KafkaHeaders.TOPIC, "funds-reservation-results")
                .setHeader("paymentId", reservation.paymentId.toString())
                .setHeader("eventType", "ReservationExpiredEvent")
                .setHeader("correlationId", UUID.randomUUID().toString())
                .build()
        )

        logger.info("Published expiration event for reservation: ${reservation.paymentId}")
    }
}
