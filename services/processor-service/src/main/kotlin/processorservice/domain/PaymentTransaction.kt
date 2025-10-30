package dev.jkiakumbo.paymentorchestrator.processorservice.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "payment_transactions")
data class PaymentTransaction(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "payment_id", nullable = false, unique = true)
    val paymentId: UUID,

    @Column(nullable = false)
    val amount: BigDecimal,

    @Column(nullable = false)
    val currency: String,

    @Column(name = "merchant_id", nullable = false)
    val merchantId: String,

    @Column(name = "customer_id", nullable = false)
    val customerId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: TransactionStatus,

    @Column(name = "external_transaction_id")
    var externalTransactionId: String? = null,

    @Column(name = "processor_reference")
    var processorReference: String? = null,

    @Column(name = "failure_reason")
    var failureReason: String? = null,

    @Column(name = "processor_response")
    var processorResponse: String? = null,

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
        this.status = TransactionStatus.PROCESSING
        this.updatedAt = LocalDateTime.now()
    }

    fun markAsCompleted(transactionId: String, processorRef: String? = null) {
        this.status = TransactionStatus.COMPLETED
        this.externalTransactionId = transactionId
        this.processorReference = processorRef
        this.updatedAt = LocalDateTime.now()
    }

    fun markAsFailed(reason: String, processorResponse: String? = null) {
        this.status = TransactionStatus.FAILED
        this.failureReason = reason
        this.processorResponse = processorResponse
        this.updatedAt = LocalDateTime.now()
    }

    fun incrementRetry() {
        this.retryCount++
        this.lastRetryAt = LocalDateTime.now()
        this.updatedAt = LocalDateTime.now()
    }

    fun canRetry(maxRetries: Int = 3): Boolean {
        return this.status == TransactionStatus.FAILED &&
                this.retryCount < maxRetries &&
                (lastRetryAt == null ||
                        lastRetryAt!!.isBefore(LocalDateTime.now().minusMinutes(5)))
    }
}

enum class TransactionStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED
}

interface PaymentTransactionRepository : org.springframework.data.repository.CrudRepository<PaymentTransaction, UUID> {
    fun findByPaymentId(paymentId: UUID): PaymentTransaction?
    fun findByExternalTransactionId(externalTransactionId: String): PaymentTransaction?
    fun findByStatusAndCreatedAtBefore(status: TransactionStatus, cutoff: LocalDateTime): List<PaymentTransaction>
}