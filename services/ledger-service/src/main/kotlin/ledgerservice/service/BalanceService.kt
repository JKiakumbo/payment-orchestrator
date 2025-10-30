package dev.jkiakumbo.paymentorchestrator.ledgerservice.service
import dev.jkiakumbo.paymentorchestrator.ledgerservice.domain.AccountBalance
import dev.jkiakumbo.paymentorchestrator.ledgerservice.domain.AccountBalanceRepository
import dev.jkiakumbo.paymentorchestrator.ledgerservice.exception.AccountBalanceNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class BalanceService(
    private val accountBalanceRepository: AccountBalanceRepository
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun getOrCreateAccountBalance(
        accountCode: String,
        accountName: String,
        accountingPeriod: String
    ): AccountBalance {
        return accountBalanceRepository.findByAccountCodeAndAccountingPeriodWithLock(accountCode, accountingPeriod)
            ?: run {
                val newBalance = AccountBalance.create(accountCode, accountName, accountingPeriod)
                accountBalanceRepository.save(newBalance)
                logger.debug("Created new account balance for $accountCode in period $accountingPeriod")
                newBalance
            }
    }

    @Transactional
    fun applyDebitEntry(accountCode: String, accountName: String, amount: BigDecimal, accountingPeriod: String) {
        val balance = getOrCreateAccountBalance(accountCode, accountName, accountingPeriod)
        balance.applyDebit(amount)
        accountBalanceRepository.save(balance)
        logger.debug("Applied debit of $amount to account $accountCode")
    }

    @Transactional
    fun applyCreditEntry(accountCode: String, accountName: String, amount: BigDecimal, accountingPeriod: String) {
        val balance = getOrCreateAccountBalance(accountCode, accountName, accountingPeriod)
        balance.applyCredit(amount)
        accountBalanceRepository.save(balance)
        logger.debug("Applied credit of $amount to account $accountCode")
    }

    @Transactional
    fun reverseDebitEntry(accountCode: String, amount: BigDecimal, accountingPeriod: String) {
        val balance = accountBalanceRepository.findByAccountCodeAndAccountingPeriodWithLock(accountCode, accountingPeriod)
            ?: throw AccountBalanceNotFoundException(accountCode, accountingPeriod)

        balance.reverseDebit(amount)
        accountBalanceRepository.save(balance)
        logger.debug("Reversed debit of $amount from account $accountCode")
    }

    @Transactional
    fun reverseCreditEntry(accountCode: String, amount: BigDecimal, accountingPeriod: String) {
        val balance = accountBalanceRepository.findByAccountCodeAndAccountingPeriodWithLock(accountCode, accountingPeriod)
            ?: throw AccountBalanceNotFoundException(accountCode, accountingPeriod)

        balance.reverseCredit(amount)
        accountBalanceRepository.save(balance)
        logger.debug("Reversed credit of $amount from account $accountCode")
    }

    @Transactional
    fun getAccountBalance(accountCode: String, accountingPeriod: String): BigDecimal {
        val balance = accountBalanceRepository.findByAccountCodeAndAccountingPeriod(accountCode, accountingPeriod)
            ?: return BigDecimal.ZERO

        return balance.netBalance
    }

    @Transactional
    fun getAccountBalances(accountingPeriod: String): Map<String, BigDecimal> {
        return accountBalanceRepository.findByAccountingPeriod(accountingPeriod)
            .associate { it.accountCode to it.netBalance }
    }
}