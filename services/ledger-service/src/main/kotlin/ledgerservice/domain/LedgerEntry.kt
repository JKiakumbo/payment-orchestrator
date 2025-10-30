package dev.jkiakumbo.paymentorchestrator.ledgerservice.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(
    name = "ledger_entries",
    indexes = [
        Index(name = "idx_ledger_payment_id", columnList = "paymentId", unique = true),
        Index(name = "idx_ledger_merchant_id", columnList = "merchantId"),
        Index(name = "idx_ledger_transaction_id", columnList = "transactionId"),
        Index(name = "idx_ledger_entry_type", columnList = "entryType"),
        Index(name = "idx_ledger_created_at", columnList = "createdAt"),
        Index(name = "idx_ledger_account_period", columnList = "accountingPeriod")
    ]
)
data class LedgerEntry(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "payment_id", nullable = false, unique = true)
    val paymentId: UUID,

    @Column(name = "transaction_id", nullable = false)
    val transactionId: String,

    @Column(name = "merchant_id", nullable = false)
    val merchantId: String,

    @Column(nullable = false, precision = 19, scale = 4)
    val amount: BigDecimal,

    @Column(nullable = false, length = 3)
    val currency: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 20)
    val entryType: LedgerEntryType,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: LedgerEntryStatus,

    @Column(name = "debit_account", nullable = false, length = 50)
    val debitAccount: String, // e.g., "MERCHANT_RECEIVABLES"

    @Column(name = "credit_account", nullable = false, length = 50)
    val creditAccount: String, // e.g., "REVENUE"

    @Column(name = "description", length = 500)
    val description: String,

    @Column(name = "accounting_period", nullable = false, length = 7)
    val accountingPeriod: String, // Format: YYYY-MM

    @Column(name = "failure_reason", length = 500)
    var failureReason: String? = null,

    @Column(name = "reversal_entry_id")
    var reversalEntryId: UUID? = null,

    @Column(name = "reversed_at")
    var reversedAt: LocalDateTime? = null,

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,

    @Column(name = "last_retry_at")
    var lastRetryAt: LocalDateTime? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    fun markAsProcessing() {
        this.status = LedgerEntryStatus.PROCESSING
        this.updatedAt = LocalDateTime.now()
    }

    fun markAsCompleted() {
        this.status = LedgerEntryStatus.COMPLETED
        this.updatedAt = LocalDateTime.now()
    }

    fun markAsFailed(reason: String) {
        this.status = LedgerEntryStatus.FAILED
        this.failureReason = reason
        this.updatedAt = LocalDateTime.now()
    }

    fun markAsReversed(reversalEntryId: UUID) {
        this.status = LedgerEntryStatus.REVERSED
        this.reversalEntryId = reversalEntryId
        this.reversedAt = LocalDateTime.now()
        this.updatedAt = LocalDateTime.now()
    }

    fun incrementRetry() {
        this.retryCount++
        this.lastRetryAt = LocalDateTime.now()
        this.updatedAt = LocalDateTime.now()
    }

    fun canRetry(maxRetries: Int = 3): Boolean {
        return this.status == LedgerEntryStatus.FAILED &&
                this.retryCount < maxRetries &&
                (lastRetryAt == null ||
                        lastRetryAt!!.isBefore(LocalDateTime.now().minusMinutes(5)))
    }

    fun getAccountingDescription(): String {
        return when (entryType) {
            LedgerEntryType.PAYMENT -> "Payment received from merchant $merchantId"
            LedgerEntryType.REVERSAL -> "Reversal of payment $paymentId"
            LedgerEntryType.ADJUSTMENT -> "Adjustment for payment $paymentId"
            LedgerEntryType.FEE -> "Processing fee for payment $paymentId"
        }
    }

    companion object {
        fun createPaymentEntry(
            paymentId: UUID,
            transactionId: String,
            merchantId: String,
            amount: BigDecimal,
            currency: String
        ): LedgerEntry {
            val currentPeriod = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"))

            return LedgerEntry(
                paymentId = paymentId,
                transactionId = transactionId,
                merchantId = merchantId,
                amount = amount,
                currency = currency,
                entryType = LedgerEntryType.PAYMENT,
                status = LedgerEntryStatus.PENDING,
                debitAccount = "MERCHANT_RECEIVABLES",
                creditAccount = "REVENUE",
                description = "Payment received from merchant $merchantId",
                accountingPeriod = currentPeriod
            )
        }

        fun createReversalEntry(
            originalEntry: LedgerEntry,
            reason: String
        ): LedgerEntry {
            val currentPeriod = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"))

            return LedgerEntry(
                paymentId = originalEntry.paymentId,
                transactionId = "REV_${originalEntry.transactionId}",
                merchantId = originalEntry.merchantId,
                amount = originalEntry.amount,
                currency = originalEntry.currency,
                entryType = LedgerEntryType.REVERSAL,
                status = LedgerEntryStatus.PENDING,
                debitAccount = originalEntry.creditAccount, // Reverse the accounts
                creditAccount = originalEntry.debitAccount,
                description = "Reversal: $reason",
                accountingPeriod = currentPeriod
            )
        }
    }
}

enum class LedgerEntryType {
    PAYMENT,
    REVERSAL,
    ADJUSTMENT,
    FEE
}

enum class LedgerEntryStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    REVERSED
}