package dev.jkiakumbo.paymentorchestrator.fundsservice.controller


import dev.jkiakumbo.paymentorchestrator.fundsservice.controller.dto.AccountBalanceResponse
import dev.jkiakumbo.paymentorchestrator.fundsservice.controller.dto.UpdateOverdraftRequest
import dev.jkiakumbo.paymentorchestrator.fundsservice.service.AccountService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/accounts")
class AccountController(
    private val accountService: AccountService
) {

    @GetMapping("/{customerId}/balance")
    fun getAccountBalance(@PathVariable customerId: String): ResponseEntity<AccountBalanceResponse> {
        return try {
            val availableBalance = accountService.getAvailableBalance(customerId)
            val totalReserved = accountService.getTotalReservedAmount(customerId)

            val response = AccountBalanceResponse(
                customerId = customerId,
                availableBalance = availableBalance,
                totalReserved = totalReserved,
                effectiveBalance = availableBalance.subtract(totalReserved)
            )

            ResponseEntity.ok(response)
        } catch (e: Exception) {
            ResponseEntity.notFound().build()
        }
    }

    @PutMapping("/{customerId}/overdraft")
    fun updateMaxOverdraft(
        @PathVariable customerId: String,
        @RequestBody request: UpdateOverdraftRequest
    ): ResponseEntity<Void> {
        return try {
            accountService.updateMaxOverdraft(customerId, request.maxOverdraft)
            ResponseEntity.ok().build()
        } catch (e: Exception) {
            ResponseEntity.notFound().build()
        }
    }
}

