package dev.jkiakumbo.paymentorchestrator.ledgerservice.exception

class LedgerException(message: String) : RuntimeException(message)

class DuplicateEntryException(paymentId: String) :
    RuntimeException("Ledger entry already exists for payment: $paymentId")

class AccountBalanceNotFoundException(accountCode: String, period: String) :
    RuntimeException("Account balance not found for account: $accountCode, period: $period")

class InvalidAccountingEntryException(message: String) : RuntimeException(message)

class LedgerReversalException(message: String) : RuntimeException(message)