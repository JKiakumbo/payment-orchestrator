package dev.jkiakumbo.paymentorchestrator.fundsservice.domain

import dev.jkiakumbo.paymentorchestrator.fundsservice.exception.InsufficientFundsException
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(
    name = "accounts",
    indexes = [
        Index(name = "idx_account_customer_id", columnList = "customerId"),
        Index(name = "idx_account_currency", columnList = "currency")
    ]
)
data class Account(
    @Id
    val customerId: String, // customerId

    @Column(nullable = false, precision = 19, scale = 4)
    var balance: BigDecimal,

    @Column(name = "available_balance", nullable = false, precision = 19, scale = 4)
    var availableBalance: BigDecimal,

    @Column(nullable = false, length = 3)
    val currency: String,

    @Column(name = "max_overdraft", nullable = false, precision = 19, scale = 4)
    var maxOverdraft: BigDecimal = BigDecimal.ZERO,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Version
    val version: Long = 0
) {
    fun hasSufficientFunds(amount: BigDecimal): Boolean {
        return availableBalance + maxOverdraft >= amount
    }

    fun reserveFunds(amount: BigDecimal) {
        if (!hasSufficientFunds(amount)) {
            throw InsufficientFundsException("Insufficient funds. Available: $availableBalance, Requested: $amount")
        }
        this.availableBalance = this.availableBalance.subtract(amount)
        this.updatedAt = LocalDateTime.now()
    }

    fun releaseFunds(amount: BigDecimal) {
        this.availableBalance = this.availableBalance.add(amount)
        this.updatedAt = LocalDateTime.now()
    }

    fun commitFunds(amount: BigDecimal) {
        this.balance = this.balance.subtract(amount)
        this.updatedAt = LocalDateTime.now()
    }

    fun refundFunds(amount: BigDecimal) {
        this.balance = this.balance.add(amount)
        this.availableBalance = this.availableBalance.add(amount)
        this.updatedAt = LocalDateTime.now()
    }
}