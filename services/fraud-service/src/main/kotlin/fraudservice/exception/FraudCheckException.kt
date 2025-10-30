package dev.jkiakumbo.paymentorchestrator.fraudservice.exception

class FraudCheckException(message: String) : RuntimeException(message)

class RuleEvaluationException(ruleId: String, message: String) :
    RuntimeException("Rule evaluation failed for $ruleId: $message")

class RiskAssessmentException(message: String) : RuntimeException(message)