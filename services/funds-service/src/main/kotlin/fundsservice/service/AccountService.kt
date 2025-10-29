package dev.jkiakumbo.paymentorchestrator.fundsservice.service

import dev.jkiakumbo.paymentorchestrator.fundsservice.domain.Account
import dev.jkiakumbo.paymentorchestrator.fundsservice.domain.ReservationStatus
import dev.jkiakumbo.paymentorchestrator.fundsservice.exception.AccountNotFoundException
import dev.jkiakumbo.paymentorchestrator.fundsservice.exception.CurrencyMismatchException
import dev.jkiakumbo.paymentorchestrator.fundsservice.repositories.AccountRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class AccountService(
    private val accountRepository: AccountRepository
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun getOrCreateAccount(customerId: String, currency: String, initialBalance: BigDecimal = BigDecimal("10000")): Account {
        return accountRepository.findByCustomerIdAndCurrency(customerId, currency) ?: run {
            val newAccount = Account(
                id = customerId,
                balance = initialBalance,
                availableBalance = initialBalance,
                currency = currency
            )
            accountRepository.save(newAccount)
            logger.info("Created new account for customer: $customerId with currency: $currency")
            newAccount
        }
    }

    @Transactional
    fun getAccountWithLock(customerId: String): Account {
        return accountRepository.findByIdWithLock(customerId)
            .orElseThrow { AccountNotFoundException(customerId) }
    }

    @Transactional
    fun validateCurrency(account: Account, requestedCurrency: String) {
        if (account.currency != requestedCurrency) {
            throw CurrencyMismatchException(
                "Account currency ${account.currency} does not match requested currency $requestedCurrency"
            )
        }
    }

    @Transactional
    fun getTotalReservedAmount(customerId: String): BigDecimal {
        return accountRepository.sumReservedAmountByCustomerAndStatus(customerId, ReservationStatus.RESERVED)
            ?: BigDecimal.ZERO
    }

    @Transactional
    fun getAvailableBalance(customerId: String): BigDecimal {
        val account = accountRepository.findById(customerId)
            .orElseThrow { AccountNotFoundException(customerId) }
        return account.availableBalance
    }

    @Transactional
    fun updateMaxOverdraft(customerId: String, maxOverdraft: BigDecimal) {
        val account = getAccountWithLock(customerId)
        account.maxOverdraft = maxOverdraft
        account.updatedAt = java.time.LocalDateTime.now()
        accountRepository.save(account)
        logger.info("Updated max overdraft for customer: $customerId to $maxOverdraft")
    }
}