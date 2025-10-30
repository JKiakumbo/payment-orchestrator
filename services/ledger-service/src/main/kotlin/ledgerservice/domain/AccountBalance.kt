package dev.jkiakumbo.paymentorchestrator.ledgerservice.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.LockModeType
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "account_balances",
    indexes = [
        Index(name = "idx_account_balance_account", columnList = "accountCode"),
        Index(name = "idx_account_balance_period", columnList = "accountingPeriod"),
        Index(name = "idx_account_balance_unique", columnList = "accountCode, accountingPeriod", unique = true)
    ]
)
data class AccountBalance(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "account_code", nullable = false, length = 50)
    val accountCode: String,

    @Column(name = "account_name", nullable = false)
    val accountName: String,

    @Column(name = "accounting_period", nullable = false, length = 7)
    val accountingPeriod: String, // Format: YYYY-MM

    @Column(name = "debit_total", nullable = false, precision = 19, scale = 4)
    var debitTotal: BigDecimal = BigDecimal.ZERO,

    @Column(name = "credit_total", nullable = false, precision = 19, scale = 4)
    var creditTotal: BigDecimal = BigDecimal.ZERO,

    @Column(name = "net_balance", nullable = false, precision = 19, scale = 4)
    var netBalance: BigDecimal = BigDecimal.ZERO,

    @Column(name = "entry_count", nullable = false)
    var entryCount: Int = 0,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Version
    val version: Long = 0
) {
    fun applyDebit(amount: BigDecimal) {
        this.debitTotal = this.debitTotal.add(amount)
        this.netBalance = this.netBalance.add(amount)
        this.entryCount++
        this.updatedAt = LocalDateTime.now()
    }

    fun applyCredit(amount: BigDecimal) {
        this.creditTotal = this.creditTotal.add(amount)
        this.netBalance = this.netBalance.subtract(amount)
        this.entryCount++
        this.updatedAt = LocalDateTime.now()
    }

    fun reverseDebit(amount: BigDecimal) {
        this.debitTotal = this.debitTotal.subtract(amount)
        this.netBalance = this.netBalance.subtract(amount)
        this.updatedAt = LocalDateTime.now()
    }

    fun reverseCredit(amount: BigDecimal) {
        this.creditTotal = this.creditTotal.subtract(amount)
        this.netBalance = this.netBalance.add(amount)
        this.updatedAt = LocalDateTime.now()
    }

    companion object {
        fun create(
            accountCode: String,
            accountName: String,
            accountingPeriod: String
        ): AccountBalance {
            return AccountBalance(
                accountCode = accountCode,
                accountName = accountName,
                accountingPeriod = accountingPeriod
            )
        }
    }
}

interface LedgerEntryRepository : org.springframework.data.repository.CrudRepository<LedgerEntry, UUID> {
    fun findByPaymentId(paymentId: UUID): LedgerEntry?
    fun findByTransactionId(transactionId: String): LedgerEntry?
    fun findByStatusAndCreatedAtBefore(status: LedgerEntryStatus, cutoff: LocalDateTime): List<LedgerEntry>
    fun findByAccountingPeriod(accountingPeriod: String): List<LedgerEntry>
    fun findByMerchantIdAndCreatedAtBetween(
        merchantId: String,
        start: LocalDateTime,
        end: LocalDateTime
    ): List<LedgerEntry>

    @org.springframework.data.jpa.repository.Query(
        "SELECT SUM(le.amount) FROM LedgerEntry le WHERE le.merchantId = :merchantId AND le.entryType = 'PAYMENT' AND le.status = 'COMPLETED'"
    )
    fun sumCompletedPaymentsByMerchant(merchantId: String): java.math.BigDecimal?
}

interface AccountBalanceRepository : org.springframework.data.repository.CrudRepository<AccountBalance, Long> {
    fun findByAccountCodeAndAccountingPeriod(accountCode: String, accountingPeriod: String): AccountBalance?
    fun findByAccountingPeriod(accountingPeriod: String): List<AccountBalance>

    @org.springframework.data.jpa.repository.Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query(
        "SELECT ab FROM AccountBalance ab WHERE ab.accountCode = :accountCode AND ab.accountingPeriod = :accountingPeriod"
    )
    fun findByAccountCodeAndAccountingPeriodWithLock(
        accountCode: String,
        accountingPeriod: String
    ): AccountBalance?
}

