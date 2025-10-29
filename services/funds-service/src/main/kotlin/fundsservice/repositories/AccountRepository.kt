package dev.jkiakumbo.paymentorchestrator.fundsservice.repositories


import dev.jkiakumbo.paymentorchestrator.fundsservice.domain.Account
import dev.jkiakumbo.paymentorchestrator.fundsservice.domain.FundReservation
import dev.jkiakumbo.paymentorchestrator.fundsservice.domain.ReservationStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import jakarta.persistence.LockModeType
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

interface AccountRepository : JpaRepository<Account, String> {
    fun findByCustomerIdAndCurrency(customerId: String, currency: String): Account?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    fun findByIdWithLock(@Param("id") id: String): Optional<Account>
}

interface FundReservationRepository : JpaRepository<FundReservation, UUID> {
    fun findByPaymentId(paymentId: UUID): FundReservation?

    fun findByCustomerId(customerId: String): List<FundReservation>

    fun findByStatusAndExpiresAtBefore(status: ReservationStatus, cutoff: LocalDateTime): List<FundReservation>

    @Query("SELECT SUM(fr.amount) FROM FundReservation fr WHERE fr.customerId = :customerId AND fr.status = :status")
    fun sumReservedAmountByCustomerAndStatus(
        @Param("customerId") customerId: String,
        @Param("status") status: ReservationStatus
    ): BigDecimal?
}