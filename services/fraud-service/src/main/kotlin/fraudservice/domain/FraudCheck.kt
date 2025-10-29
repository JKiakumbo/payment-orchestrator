package dev.jkiakumbo.paymentorchestrator.fraudservice.domain


import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "fraud_checks")
data class FraudCheck(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    val paymentId: UUID,

    @Column(nullable = false)
    val amount: java.math.BigDecimal,

    @Column(nullable = false)
    val currency: String,

    @Column(nullable = false)
    val merchantId: String,

    @Column(nullable = false)
    val customerId: String,

    @Column(nullable = false)
    var status: FraudCheckStatus,

    @Column
    var reason: String? = null,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)

enum class FraudCheckStatus {
    PENDING, APPROVED, DECLINED
}

interface FraudCheckRepository : org.springframework.data.repository.CrudRepository<FraudCheck, UUID> {
    fun findByPaymentId(paymentId: UUID): FraudCheck?
}