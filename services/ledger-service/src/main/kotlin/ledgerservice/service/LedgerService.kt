package dev.jkiakumbo.paymentorchestrator.ledgerservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import dev.jkiakumbo.paymentorchestrator.ledgerservice.domain.LedgerEntry
import dev.jkiakumbo.paymentorchestrator.ledgerservice.domain.LedgerEntryRepository
import dev.jkiakumbo.paymentorchestrator.ledgerservice.domain.LedgerEntryStatus
import dev.jkiakumbo.paymentorchestrator.ledgerservice.events.LedgerReversedEvent
import dev.jkiakumbo.paymentorchestrator.ledgerservice.events.LedgerUpdateFailedEvent
import dev.jkiakumbo.paymentorchestrator.ledgerservice.events.LedgerUpdateRequestedEvent
import dev.jkiakumbo.paymentorchestrator.ledgerservice.events.LedgerUpdatedEvent
import dev.jkiakumbo.paymentorchestrator.ledgerservice.exception.InvalidAccountingEntryException
import dev.jkiakumbo.paymentorchestrator.ledgerservice.exception.LedgerException
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.support.MessageBuilder
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Service
class LedgerService(
    private val ledgerEntryRepository: LedgerEntryRepository,
    private val balanceService: BalanceService,
    private val objectMapper: ObjectMapper,
    private val kafkaTemplate: KafkaTemplate<String, String>
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun processLedgerUpdate(event: LedgerUpdateRequestedEvent) {
        logger.info("Processing ledger update for payment: ${event.paymentId}, transaction: ${event.transactionId}")

        // Check idempotency - if we already processed this payment
        val existingEntry = ledgerEntryRepository.findByPaymentId(event.paymentId)
        if (existingEntry != null) {
            handleExistingEntry(existingEntry, event)
            return
        }

        // Create new ledger entry
        val ledgerEntry = LedgerEntry.createPaymentEntry(
            paymentId = event.paymentId,
            transactionId = event.transactionId,
            merchantId = event.merchantId,
            amount = event.amount,
            currency = event.currency
        )

        ledgerEntryRepository.save(ledgerEntry)

        // Process the ledger entry
        processLedgerEntry(ledgerEntry)
    }

    private fun handleExistingEntry(ledgerEntry: LedgerEntry, event: LedgerUpdateRequestedEvent) {
        logger.info("Ledger entry already exists for payment: ${event.paymentId}, status: ${ledgerEntry.status}")

        when (ledgerEntry.status) {
            LedgerEntryStatus.COMPLETED -> {
                // Idempotency - already succeeded, publish success event
                publishSuccessEvent(ledgerEntry)
            }
            LedgerEntryStatus.FAILED -> {
                if (ledgerEntry.canRetry()) {
                    logger.info("Retrying failed ledger entry: ${event.paymentId}, retry count: ${ledgerEntry.retryCount}")
                    ledgerEntry.incrementRetry()
                    ledgerEntry.markAsProcessing()
                    ledgerEntryRepository.save(ledgerEntry)

                    // Retry processing
                    processLedgerEntry(ledgerEntry)
                } else {
                    logger.warn("Ledger entry ${event.paymentId} has exceeded maximum retry attempts")
                    // Do nothing - already failed and cannot retry
                }
            }
            LedgerEntryStatus.PROCESSING -> {
                logger.info("Ledger entry ${event.paymentId} is already being processed")
                // Do nothing - already processing
            }
            else -> {
                // For other statuses, retry processing
                processLedgerEntry(ledgerEntry)
            }
        }
    }

    @Retryable(
        retryFor = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 1000, multiplier = 2.0)
    )
    @Transactional
    fun processLedgerEntry(ledgerEntry: LedgerEntry) {
        try {
            ledgerEntry.markAsProcessing()
            ledgerEntryRepository.save(ledgerEntry)

            logger.info("Processing ledger entry for payment: ${ledgerEntry.paymentId}")

            // Validate the entry
            validateLedgerEntry(ledgerEntry)

            // Apply double-entry accounting
            applyDoubleEntryAccounting(ledgerEntry)

            // Mark as completed
            ledgerEntry.markAsCompleted()
            ledgerEntryRepository.save(ledgerEntry)

            publishSuccessEvent(ledgerEntry)
            logger.info("Ledger entry completed for payment: ${ledgerEntry.paymentId}")

        } catch (e: Exception) {
            logger.error("Ledger entry processing failed for payment: ${ledgerEntry.paymentId}", e)

            ledgerEntry.markAsFailed(e.message ?: "Unknown error")
            ledgerEntryRepository.save(ledgerEntry)

            val canRetry = e !is InvalidAccountingEntryException && ledgerEntry.canRetry()

            publishFailureEvent(ledgerEntry, e.message ?: "Unknown error", canRetry)
        }
    }

    @Transactional
    fun reverseLedgerEntry(paymentId: UUID, reason: String) {
        logger.info("Reversing ledger entry for payment: $paymentId, reason: $reason")

        val originalEntry = ledgerEntryRepository.findByPaymentId(paymentId)
            ?: throw LedgerException("Ledger entry not found for payment: $paymentId")

        if (originalEntry.status != LedgerEntryStatus.COMPLETED) {
            throw LedgerException("Cannot reverse ledger entry with status: ${originalEntry.status}")
        }

        // Create reversal entry
        val reversalEntry = LedgerEntry.createReversalEntry(originalEntry, reason)
        ledgerEntryRepository.save(reversalEntry)

        try {
            // Process the reversal
            processReversalEntry(originalEntry, reversalEntry)

            logger.info("Successfully reversed ledger entry for payment: $paymentId")
        } catch (e: Exception) {
            logger.error("Failed to reverse ledger entry for payment: $paymentId", e)
            reversalEntry.markAsFailed(e.message ?: "Unknown error")
            ledgerEntryRepository.save(reversalEntry)
            throw e
        }
    }

    @Transactional
    fun processReversalEntry(originalEntry: LedgerEntry, reversalEntry: LedgerEntry) {
        reversalEntry.markAsProcessing()
        ledgerEntryRepository.save(reversalEntry)

        // Apply reverse double-entry accounting
        applyReverseDoubleEntryAccounting(originalEntry, reversalEntry)

        // Mark both entries appropriately
        reversalEntry.markAsCompleted()
        originalEntry.markAsReversed(reversalEntry.id)

        ledgerEntryRepository.save(reversalEntry)
        ledgerEntryRepository.save(originalEntry)

        publishReversalEvent(originalEntry, reversalEntry)
    }

    private fun validateLedgerEntry(ledgerEntry: LedgerEntry) {
        if (ledgerEntry.amount <= BigDecimal.ZERO) {
            throw InvalidAccountingEntryException("Ledger entry amount must be positive")
        }

        if (ledgerEntry.debitAccount.isBlank() || ledgerEntry.creditAccount.isBlank()) {
            throw InvalidAccountingEntryException("Both debit and credit accounts must be specified")
        }

        if (ledgerEntry.debitAccount == ledgerEntry.creditAccount) {
            throw InvalidAccountingEntryException("Debit and credit accounts cannot be the same")
        }
    }

    private fun applyDoubleEntryAccounting(ledgerEntry: LedgerEntry) {
        // Apply debit to debit account
        balanceService.applyDebitEntry(
            accountCode = ledgerEntry.debitAccount,
            accountName = getAccountName(ledgerEntry.debitAccount),
            amount = ledgerEntry.amount,
            accountingPeriod = ledgerEntry.accountingPeriod
        )

        // Apply credit to credit account
        balanceService.applyCreditEntry(
            accountCode = ledgerEntry.creditAccount,
            accountName = getAccountName(ledgerEntry.creditAccount),
            amount = ledgerEntry.amount,
            accountingPeriod = ledgerEntry.accountingPeriod
        )
    }

    private fun applyReverseDoubleEntryAccounting(originalEntry: LedgerEntry, reversalEntry: LedgerEntry) {
        // Reverse the original debit (apply credit to original debit account)
        balanceService.reverseDebitEntry(
            accountCode = originalEntry.debitAccount,
            amount = originalEntry.amount,
            accountingPeriod = originalEntry.accountingPeriod
        )

        // Reverse the original credit (apply debit to original credit account)
        balanceService.reverseCreditEntry(
            accountCode = originalEntry.creditAccount,
            amount = originalEntry.amount,
            accountingPeriod = originalEntry.accountingPeriod
        )
    }

    private fun getAccountName(accountCode: String): String {
        return when (accountCode) {
            "MERCHANT_RECEIVABLES" -> "Merchant Receivables"
            "REVENUE" -> "Revenue"
            "CASH" -> "Cash"
            "FEES" -> "Processing Fees"
            "CHARGEBACKS" -> "Chargebacks"
            else -> accountCode
        }
    }

    @Transactional
    fun findStuckEntries(): List<LedgerEntry> {
        val cutoff = java.time.LocalDateTime.now().minusMinutes(10)
        return ledgerEntryRepository.findByStatusAndCreatedAtBefore(LedgerEntryStatus.PROCESSING, cutoff)
    }

    @Transactional
    fun retryStuckEntry(paymentId: UUID) {
        val ledgerEntry = ledgerEntryRepository.findByPaymentId(paymentId)
            ?: throw IllegalArgumentException("Ledger entry not found: $paymentId")

        if (ledgerEntry.status == LedgerEntryStatus.PROCESSING) {
            logger.warn("Retrying stuck ledger entry: $paymentId")
            ledgerEntry.markAsFailed("Stuck entry - manual retry")
            ledgerEntryRepository.save(ledgerEntry)

            // This will trigger a retry in the next ledger update request
        }
    }

    private fun publishSuccessEvent(ledgerEntry: LedgerEntry) {
        val event = LedgerUpdatedEvent(
            paymentId = ledgerEntry.paymentId,
            ledgerEntryId = ledgerEntry.id.toString()
        )

        kafkaTemplate.send(
            MessageBuilder.withPayload(objectMapper.writeValueAsString(event))
                .setHeader(KafkaHeaders.TOPIC, "ledger-update-results")
                .setHeader("paymentId", ledgerEntry.paymentId.toString())
                .setHeader("eventType", "LedgerUpdatedEvent")
                .setHeader("correlationId", UUID.randomUUID().toString())
                .build()
        )
    }

    private fun publishFailureEvent(ledgerEntry: LedgerEntry, reason: String, canRetry: Boolean) {
        val event = LedgerUpdateFailedEvent(
            paymentId = ledgerEntry.paymentId,
            reason = reason,
            canRetry = canRetry
        )

        kafkaTemplate.send(
            MessageBuilder.withPayload(objectMapper.writeValueAsString(event))
                .setHeader(KafkaHeaders.TOPIC, "ledger-update-results")
                .setHeader("paymentId", ledgerEntry.paymentId.toString())
                .setHeader("eventType", "LedgerUpdateFailedEvent")
                .setHeader("correlationId", UUID.randomUUID().toString())
                .build()
        )
    }

    private fun publishReversalEvent(originalEntry: LedgerEntry, reversalEntry: LedgerEntry) {
        val event = LedgerReversedEvent(
            paymentId = originalEntry.paymentId,
            originalEntryId = originalEntry.id.toString(),
            reversalEntryId = reversalEntry.id.toString(),
            reason = reversalEntry.description
        )

        kafkaTemplate.send(
            MessageBuilder.withPayload(objectMapper.writeValueAsString(event))
                .setHeader(KafkaHeaders.TOPIC, "ledger-reversal-events")
                .setHeader("paymentId", originalEntry.paymentId.toString())
                .setHeader("eventType", "LedgerReversedEvent")
                .setHeader("correlationId", UUID.randomUUID().toString())
                .build()
        )
    }
}