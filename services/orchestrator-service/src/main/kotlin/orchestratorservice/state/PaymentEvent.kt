package dev.jkiakumbo.paymentorchestrator.orchestratorservice.state

enum class PaymentEvent {
    // Initial events
    INITIATE_PAYMENT,

    // Fraud check events
    FRAUD_CHECK_REQUESTED,
    FRAUD_CHECK_APPROVED,
    FRAUD_CHECK_DECLINED,

    // Funds reservation events
    FUNDS_RESERVATION_REQUESTED,
    FUNDS_RESERVED,
    FUNDS_RESERVATION_FAILED,

    // Payment execution events
    PROCESSOR_EXECUTION_REQUESTED,
    PROCESSOR_EXECUTED,
    PROCESSOR_EXECUTION_FAILED,

    // Ledger update events
    LEDGER_UPDATE_REQUESTED,
    LEDGER_UPDATED,
    LEDGER_UPDATE_FAILED,

    // Compensation events
    COMPENSATION_TRIGGERED,
    COMPENSATION_COMPLETED,

    // Manual intervention events
    MANUAL_RETRY,
    MANUAL_CANCEL
}