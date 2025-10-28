package dev.jkiakumbo.paymentorchestrator.state

import org.springframework.statemachine.config.EnableStateMachine
import org.springframework.statemachine.config.StateMachineConfigurerAdapter
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer
import org.springframework.stereotype.Component

@Component
@EnableStateMachine
class PaymentStateMachineConfig : StateMachineConfigurerAdapter<PaymentState, PaymentEvent>() {

    override fun configure(states: StateMachineStateConfigurer<PaymentState, PaymentEvent>) {
        states
            .withStates()
            .initial(PaymentState.INITIATED)
            .state(PaymentState.FRAUD_CHECK_PENDING)
            .state(PaymentState.FRAUD_CHECK_COMPLETED)
            .state(PaymentState.FRAUD_CHECK_FAILED)
            .state(PaymentState.FUNDS_RESERVATION_PENDING)
            .state(PaymentState.FUNDS_RESERVED)
            .state(PaymentState.FUNDS_RESERVATION_FAILED)
            .state(PaymentState.PROCESSOR_EXECUTION_PENDING)
            .state(PaymentState.PROCESSOR_EXECUTED)
            .state(PaymentState.PROCESSOR_EXECUTION_FAILED)
            .state(PaymentState.LEDGER_UPDATE_PENDING)
            .state(PaymentState.COMPLETED)
            .state(PaymentState.FAILED)
            .state(PaymentState.COMPENSATING)
            .state(PaymentState.COMPENSATED)
    }

    override fun configure(transitions: StateMachineTransitionConfigurer<PaymentState, PaymentEvent>) {
        transitions
            .withExternal()
            .source(PaymentState.INITIATED).target(PaymentState.FRAUD_CHECK_PENDING)
            .event(PaymentEvent.FRAUD_CHECK_REQUESTED)
            .and()
            .withExternal()
            .source(PaymentState.FRAUD_CHECK_PENDING).target(PaymentState.FRAUD_CHECK_COMPLETED)
            .event(PaymentEvent.FRAUD_CHECK_APPROVED)
            .and()
            .withExternal()
            .source(PaymentState.FRAUD_CHECK_PENDING).target(PaymentState.FRAUD_CHECK_FAILED)
            .event(PaymentEvent.FRAUD_CHECK_DECLINED)
            .and()
            .withExternal()
            .source(PaymentState.FRAUD_CHECK_COMPLETED).target(PaymentState.FUNDS_RESERVATION_PENDING)
            .event(PaymentEvent.FUNDS_RESERVATION_REQUESTED)
            .and()
            .withExternal()
            .source(PaymentState.FUNDS_RESERVATION_PENDING).target(PaymentState.FUNDS_RESERVED)
            .event(PaymentEvent.FUNDS_RESERVED)
            .and()
            .withExternal()
            .source(PaymentState.FUNDS_RESERVATION_PENDING).target(PaymentState.FUNDS_RESERVATION_FAILED)
            .event(PaymentEvent.FUNDS_RESERVATION_FAILED)
            .and()
            .withExternal()
            .source(PaymentState.FUNDS_RESERVED).target(PaymentState.PROCESSOR_EXECUTION_PENDING)
            .event(PaymentEvent.PROCESSOR_EXECUTION_REQUESTED)
            .and()
            .withExternal()
            .source(PaymentState.PROCESSOR_EXECUTION_PENDING).target(PaymentState.PROCESSOR_EXECUTED)
            .event(PaymentEvent.PROCESSOR_EXECUTED)
            .and()
            .withExternal()
            .source(PaymentState.PROCESSOR_EXECUTION_PENDING).target(PaymentState.PROCESSOR_EXECUTION_FAILED)
            .event(PaymentEvent.PROCESSOR_EXECUTION_FAILED)
            .and()
            .withExternal()
            .source(PaymentState.PROCESSOR_EXECUTED).target(PaymentState.LEDGER_UPDATE_PENDING)
            .event(PaymentEvent.LEDGER_UPDATE_REQUESTED)
            .and()
            .withExternal()
            .source(PaymentState.LEDGER_UPDATE_PENDING).target(PaymentState.COMPLETED)
            .event(PaymentEvent.LEDGER_UPDATED)
            .and()
            .withExternal()
            .source(PaymentState.LEDGER_UPDATE_PENDING).target(PaymentState.LEDGER_UPDATE_FAILED)
            .event(PaymentEvent.LEDGER_UPDATE_FAILED)
            .and()
            .withExternal()
            .source(PaymentState.FUNDS_RESERVATION_FAILED).target(PaymentState.COMPENSATING)
            .event(PaymentEvent.COMPENSATION_TRIGGERED)
            .and()
            .withExternal()
            .source(PaymentState.PROCESSOR_EXECUTION_FAILED).target(PaymentState.COMPENSATING)
            .event(PaymentEvent.COMPENSATION_TRIGGERED)
            .and()
            .withExternal()
            .source(PaymentState.LEDGER_UPDATE_FAILED).target(PaymentState.COMPENSATING)
            .event(PaymentEvent.COMPENSATION_TRIGGERED)
            .and()
            .withExternal()
            .source(PaymentState.COMPENSATING).target(PaymentState.COMPENSATED)
            .event(PaymentEvent.COMPENSATION_COMPLETED)
    }
}