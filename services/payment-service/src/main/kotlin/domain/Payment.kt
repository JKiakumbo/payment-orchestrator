package dev.jkiakumbo.paymentorchestrator.domain

import dev.jkiakumbo.paymentorchestrator.state.PaymentState
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
@Table(name = "payments")
data class Payment(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    val amount: BigDecimal,

    @Column(nullable = false)
    val currency: String,

    @Column(nullable = false)
    val merchantId: String,

    @Column(nullable = false)
    val customerId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var state: PaymentState,

    @Column
    var failureReason: String? = null,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column
    var updatedAt: LocalDateTime = LocalDateTime.now()
)

interface PaymentRepository : org.springframework.data.repository.CrudRepository<Payment, UUID> {
    fun findByMerchantId(merchantId: String): List<Payment>
}