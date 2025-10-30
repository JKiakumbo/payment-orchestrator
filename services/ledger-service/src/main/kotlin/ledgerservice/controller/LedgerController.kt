package dev.jkiakumbo.paymentorchestrator.ledgerservice.controller

import dev.jkiakumbo.paymentorchestrator.ledgerservice.controller.dto.AccountBalanceResponse
import dev.jkiakumbo.paymentorchestrator.ledgerservice.controller.dto.CreateLedgerEntryRequest
import dev.jkiakumbo.paymentorchestrator.ledgerservice.controller.dto.LedgerEntryResponse
import dev.jkiakumbo.paymentorchestrator.ledgerservice.controller.dto.LedgerEntryStatusResponse
import dev.jkiakumbo.paymentorchestrator.ledgerservice.controller.dto.ReverseLedgerEntryRequest
import dev.jkiakumbo.paymentorchestrator.ledgerservice.service.BalanceService
import dev.jkiakumbo.paymentorchestrator.ledgerservice.service.LedgerService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.YearMonth
import java.util.UUID

@RestController
@RequestMapping("/api/ledger")
class LedgerController(
    private val ledgerService: LedgerService,
    private val balanceService: BalanceService
) {

    @PostMapping("/entries")
    fun createLedgerEntry(@RequestBody request: CreateLedgerEntryRequest): ResponseEntity<LedgerEntryResponse> {
        return try {
            // Convert to event and process
            val event = request.toEvent()
            ledgerService.processLedgerUpdate(event)

            val response = LedgerEntryResponse(
                paymentId = request.paymentId,
                status = "PROCESSING",
                message = "Ledger entry initiated"
            )

            ResponseEntity.ok(response)
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(
                LedgerEntryResponse(
                    paymentId = request.paymentId,
                    status = "FAILED",
                    message = e.message ?: "Unknown error"
                )
            )
        }
    }

    @GetMapping("/entries/{paymentId}")
    fun getLedgerEntry(@PathVariable paymentId: UUID): ResponseEntity<LedgerEntryStatusResponse> {
        // This would typically query the database for the ledger entry status
        // For now, we'll return a mock response
        return ResponseEntity.ok(
            LedgerEntryStatusResponse(
                paymentId = paymentId,
                status = "COMPLETED",
                ledgerEntryId = UUID.randomUUID().toString()
            )
        )
    }

    @GetMapping("/balances/{accountCode}")
    fun getAccountBalance(
        @PathVariable accountCode: String,
        @RequestParam(required = false) period: String?
    ): ResponseEntity<AccountBalanceResponse> {
        val accountingPeriod = period ?: YearMonth.now().toString()

        return try {
            val balance = balanceService.getAccountBalance(accountCode, accountingPeriod)
            val response = AccountBalanceResponse(
                accountCode = accountCode,
                accountingPeriod = accountingPeriod,
                balance = balance
            )
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/balances")
    fun getAllAccountBalances(
        @RequestParam(required = false) period: String?
    ): ResponseEntity<Map<String, BigDecimal>> {
        val accountingPeriod = period ?: YearMonth.now().toString()

        return try {
            val balances = balanceService.getAccountBalances(accountingPeriod)
            ResponseEntity.ok(balances)
        } catch (e: Exception) {
            ResponseEntity.ok(emptyMap())
        }
    }

    @PostMapping("/entries/{paymentId}/reverse")
    fun reverseLedgerEntry(
        @PathVariable paymentId: UUID,
        @RequestBody request: ReverseLedgerEntryRequest
    ): ResponseEntity<Map<String, String>> {
        return try {
            ledgerService.reverseLedgerEntry(paymentId, request.reason)
            ResponseEntity.ok(mapOf("message" to "Ledger entry reversed successfully"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Unknown error")))
        }
    }

    @PostMapping("/stuck-entries/retry/{paymentId}")
    fun retryStuckEntry(@PathVariable paymentId: UUID): ResponseEntity<Map<String, String>> {
        return try {
            ledgerService.retryStuckEntry(paymentId)
            ResponseEntity.ok(mapOf("message" to "Retry initiated for stuck entry: $paymentId"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Unknown error")))
        }
    }
}
