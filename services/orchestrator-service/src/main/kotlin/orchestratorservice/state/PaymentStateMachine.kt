package dev.jkiakumbo.paymentorchestrator.orchestratorservice.state

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.Message
import org.springframework.statemachine.StateMachine
import org.springframework.statemachine.annotation.OnStateChanged
import org.springframework.statemachine.annotation.WithStateMachine
import org.springframework.statemachine.config.EnableStateMachineFactory
import org.springframework.statemachine.config.StateMachineConfigurerAdapter
import org.springframework.statemachine.config.StateMachineFactory
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer
import org.springframework.statemachine.listener.StateMachineListenerAdapter
import org.springframework.statemachine.service.DefaultStateMachineService
import org.springframework.statemachine.service.StateMachineService
import org.springframework.statemachine.state.State
import org.springframework.stereotype.Component

@Configuration
@EnableStateMachineFactory
class PaymentStateMachineConfig : StateMachineConfigurerAdapter<PaymentState, PaymentEvent>() {

    private val logger = LoggerFactory.getLogger(javaClass)

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
            // Initial transition
            .withExternal()
            .source(PaymentState.INITIATED).target(PaymentState.FRAUD_CHECK_PENDING)
            .event(PaymentEvent.FRAUD_CHECK_REQUESTED)
            .and()

            // Fraud check transitions
            .withExternal()
            .source(PaymentState.FRAUD_CHECK_PENDING).target(PaymentState.FRAUD_CHECK_COMPLETED)
            .event(PaymentEvent.FRAUD_CHECK_APPROVED)
            .and()
            .withExternal()
            .source(PaymentState.FRAUD_CHECK_PENDING).target(PaymentState.FRAUD_CHECK_FAILED)
            .event(PaymentEvent.FRAUD_CHECK_DECLINED)
            .and()

            // Funds reservation transitions
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

            // Payment execution transitions
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

            // Ledger update transitions
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

            // Compensation transitions
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
            .and()

            // Manual retry transitions
            .withExternal()
            .source(PaymentState.FRAUD_CHECK_FAILED).target(PaymentState.FRAUD_CHECK_PENDING)
            .event(PaymentEvent.MANUAL_RETRY)
            .and()
            .withExternal()
            .source(PaymentState.FUNDS_RESERVATION_FAILED).target(PaymentState.FUNDS_RESERVATION_PENDING)
            .event(PaymentEvent.MANUAL_RETRY)
            .and()
            .withExternal()
            .source(PaymentState.PROCESSOR_EXECUTION_FAILED).target(PaymentState.PROCESSOR_EXECUTION_PENDING)
            .event(PaymentEvent.MANUAL_RETRY)
            .and()
            .withExternal()
            .source(PaymentState.LEDGER_UPDATE_FAILED).target(PaymentState.LEDGER_UPDATE_PENDING)
            .event(PaymentEvent.MANUAL_RETRY)
    }

    override fun configure(config: StateMachineConfigurationConfigurer<PaymentState, PaymentEvent>) {
        config
            .withConfiguration()
            .autoStartup(true)
    }

    @Bean
    fun stateMachineService(stateMachineFactory: StateMachineFactory<PaymentState, PaymentEvent>): StateMachineService<PaymentState, PaymentEvent> {
        return DefaultStateMachineService(stateMachineFactory)
    }

    @Bean
    fun stateMachineListener(): StateMachineListenerAdapter<PaymentState, PaymentEvent> {
        return object : StateMachineListenerAdapter<PaymentState, PaymentEvent>() {
            override fun stateChanged(from: State<PaymentState, PaymentEvent>?, to: State<PaymentState, PaymentEvent>?) {
                logger.info("State changed from ${from?.id} to ${to?.id}")
            }

            override fun eventNotAccepted(event: Message<PaymentEvent>?) {
                logger.warn("Event not accepted: ${event?.payload}")
            }

            override fun stateMachineError(stateMachine: StateMachine<PaymentState, PaymentEvent>?, exception: Exception?) {
                logger.error("State machine error", exception)
            }
        }
    }
}

@Component
@WithStateMachine
class PaymentStateMachineListener {

    private val logger = LoggerFactory.getLogger(javaClass)

    @OnStateChanged
    fun stateChanged(state: State<PaymentState, PaymentEvent>) {
        logger.debug("Payment state changed to: ${state.id}")
    }
}