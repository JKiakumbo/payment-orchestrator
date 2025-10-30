package dev.jkiakumbo.paymentorchestrator.orchestratorservice.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "payments",
    indexes = [
        Index(name = "idx_payment_merchant_id", columnList = "merchantId"),
        Index(name = "idx_payment_customer_id", columnList = "customerId"),
        Index(name = "idx_payment_state", columnList = "state"),
        Index(name = "idx_payment_created_at", columnList = "createdAt")
    ]
)
data class Payment(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false, precision = 19, scale = 4)
    val amount: BigDecimal,

    @Column(nullable = false, length = 3)
    val currency: String,

    @Column(name = "merchant_id", nullable = false)
    val merchantId: String,

    @Column(name = "customer_id", nullable = false)
    val customerId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    var state: PaymentState,

    @Column(name = "current_step", length = 50)
    var currentStep: String? = null,

    @Column(name = "transaction_id")
    var transactionId: String? = null,

    @Column(name = "reservation_id")
    var reservationId: String? = null,

    @Column(name = "ledger_entry_id")
    var ledgerEntryId: String? = null,

    @Column(name = "failure_reason", length = 1000)
    var failureReason: String? = null,

    @Column(name = "compensation_reason", length = 1000)
    var compensationReason: String? = null,

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,

    @Column(name = "last_retry_at")
    var lastRetryAt: LocalDateTime? = null,

    @Column(name = "correlation_id")
    var correlationId: String? = null,

    @Column(name = "trace_id")
    var traceId: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "completed_at")
    var completedAt: LocalDateTime? = null
) {
    fun markStep(step: String) {
        this.currentStep = step
        this.updatedAt = LocalDateTime.now()
    }

    fun markFailed(reason: String) {
        this.failureReason = reason
        this.updatedAt = LocalDateTime.now()
    }

    fun markCompensated(reason: String) {
        this.compensationReason = reason
        this.updatedAt = LocalDateTime.now()
    }

    fun incrementRetry() {
        this.retryCount++
        this.lastRetryAt = LocalDateTime.now()
        this.updatedAt = LocalDateTime.now()
    }

    fun markCompleted() {
        this.completedAt = LocalDateTime.now()
        this.updatedAt = LocalDateTime.now()
    }

    fun canRetry(maxRetries: Int = 3): Boolean {
        return this.retryCount < maxRetries &&
                (lastRetryAt == null ||
                        lastRetryAt!!.isBefore(LocalDateTime.now().minusMinutes(5)))
    }
}

enum class PaymentState {
    // Initial states
    INITIATED,

    // Fraud check states
    FRAUD_CHECK_PENDING,
    FRAUD_CHECK_COMPLETED,
    FRAUD_CHECK_FAILED,

    // Funds reservation states
    FUNDS_RESERVATION_PENDING,
    FUNDS_RESERVED,
    FUNDS_RESERVATION_FAILED,

    // Payment execution states
    PROCESSOR_EXECUTION_PENDING,
    PROCESSOR_EXECUTED,
    PROCESSOR_EXECUTION_FAILED,

    // Ledger update states
    LEDGER_UPDATE_PENDING,
    COMPLETED,
    LEDGER_UPDATE_FAILED,

    // Compensation states
    COMPENSATING,
    COMPENSATED,

    // Final states
    FAILED,
    CANCELLED
}

interface PaymentRepository : org.springframework.data.repository.CrudRepository<Payment, UUID> {
    fun findByMerchantId(merchantId: String): List<Payment>
    fun findByStateAndUpdatedAtBefore(state: PaymentState, cutoff: LocalDateTime): List<Payment>
    fun findByCustomerId(customerId: String): List<Payment>

    @org.springframework.data.jpa.repository.Query(
        "SELECT p FROM Payment p WHERE p.state IN :states AND p.updatedAt < :cutoff"
    )
    fun findStuckPayments(states: List<PaymentState>, cutoff: LocalDateTime): List<Payment>
}