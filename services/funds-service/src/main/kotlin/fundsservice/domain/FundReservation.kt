package dev.jkiakumbo.paymentorchestrator.fundsservice.domain


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
    name = "fund_reservations",
    indexes = [
        Index(name = "idx_reservation_payment_id", columnList = "paymentId", unique = true),
        Index(name = "idx_reservation_customer_id", columnList = "customerId"),
        Index(name = "idx_reservation_status", columnList = "status"),
        Index(name = "idx_reservation_created_at", columnList = "createdAt")
    ]
)
data class FundReservation(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "payment_id", nullable = false, unique = true)
    val paymentId: UUID,

    @Column(name = "customer_id", nullable = false)
    val customerId: String,

    @Column(nullable = false, precision = 19, scale = 4)
    val amount: BigDecimal,

    @Column(nullable = false, length = 3)
    val currency: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ReservationStatus,

    @Column(name = "reserved_at")
    var reservedAt: LocalDateTime? = null,

    @Column(name = "released_at")
    var releasedAt: LocalDateTime? = null,

    @Column(name = "committed_at")
    var committedAt: LocalDateTime? = null,

    @Column(name = "failure_reason", length = 500)
    var failureReason: String? = null,

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,

    @Column(name = "last_retry_at")
    var lastRetryAt: LocalDateTime? = null,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: LocalDateTime,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    fun markAsReserved() {
        this.status = ReservationStatus.RESERVED
        this.reservedAt = LocalDateTime.now()
        this.updatedAt = LocalDateTime.now()
    }

    fun markAsReleased() {
        this.status = ReservationStatus.RELEASED
        this.releasedAt = LocalDateTime.now()
        this.updatedAt = LocalDateTime.now()
    }

    fun markAsCommitted() {
        this.status = ReservationStatus.COMMITTED
        this.committedAt = LocalDateTime.now()
        this.updatedAt = LocalDateTime.now()
    }

    fun markAsFailed(reason: String) {
        this.status = ReservationStatus.FAILED
        this.failureReason = reason
        this.updatedAt = LocalDateTime.now()
    }

    fun markAsExpired() {
        this.status = ReservationStatus.EXPIRED
        this.updatedAt = LocalDateTime.now()
    }

    fun incrementRetry() {
        this.retryCount++
        this.lastRetryAt = LocalDateTime.now()
        this.updatedAt = LocalDateTime.now()
    }

    fun canRetry(maxRetries: Int = 3): Boolean {
        return this.status == ReservationStatus.FAILED &&
                this.retryCount < maxRetries &&
                (lastRetryAt == null ||
                        lastRetryAt!!.isBefore(LocalDateTime.now().minusMinutes(5)))
    }

    fun isExpired(): Boolean {
        return LocalDateTime.now().isAfter(expiresAt)
    }

    companion object {
        fun create(
            paymentId: UUID,
            customerId: String,
            amount: BigDecimal,
            currency: String,
            reservationTimeoutMinutes: Long = 30
        ): FundReservation {
            return FundReservation(
                paymentId = paymentId,
                customerId = customerId,
                amount = amount,
                currency = currency,
                status = ReservationStatus.PENDING,
                expiresAt = LocalDateTime.now().plusMinutes(reservationTimeoutMinutes)
            )
        }
    }
}

enum class ReservationStatus {
    PENDING,
    RESERVED,
    COMMITTED,
    RELEASED,
    FAILED,
    EXPIRED
}