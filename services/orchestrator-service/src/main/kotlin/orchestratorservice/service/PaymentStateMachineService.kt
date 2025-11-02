package dev.jkiakumbo.paymentorchestrator.orchestratorservice.service

import dev.jkiakumbo.paymentorchestrator.orchestratorservice.state.PaymentEvent
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.state.PaymentState
import org.slf4j.LoggerFactory
import org.springframework.statemachine.StateMachine
import org.springframework.statemachine.service.StateMachineService
import org.springframework.stereotype.Service
import java.util.*

@Service
class PaymentStateMachineService(
    private val stateMachineService: StateMachineService<PaymentState, PaymentEvent>
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun getStateMachine(paymentId: UUID): StateMachine<PaymentState, PaymentEvent> {
        return stateMachineService.acquireStateMachine(paymentId.toString())
    }

    fun sendEvent(paymentId: UUID, event: PaymentEvent): Boolean {
        return try {
            val stateMachine = getStateMachine(paymentId)
            val result = stateMachine.sendEvent(event)
            logger.debug("Sent event $event to payment $paymentId, result: $result")
            result
        } catch (e: Exception) {
            logger.error("Failed to send event $event to payment $paymentId", e)
            false
        }
    }

    fun getCurrentState(paymentId: UUID): PaymentState {
        return getStateMachine(paymentId).state.id
    }

    fun startStateMachine(paymentId: UUID) {
        val stateMachine = getStateMachine(paymentId)
        // Check if state machine is already started by looking at the initial state
        if (stateMachine.state.id == PaymentState.INITIATED) {
            stateMachine.start()
            logger.debug("Started state machine for payment: $paymentId")
        } else {
            logger.debug("State machine for payment $paymentId already started, current state: ${stateMachine.state.id}")
        }
    }

    fun stopStateMachine(paymentId: UUID) {
        val stateMachine = getStateMachine(paymentId)
        // Check if state machine is not in a final state before stopping
        if (!isFinalState(stateMachine.state.id)) {
            stateMachine.stop()
            logger.debug("Stopped state machine for payment: $paymentId")
        } else {
            logger.debug("State machine for payment $paymentId is in final state: ${stateMachine.state.id}")
        }
    }

    fun resetStateMachine(paymentId: UUID) {
        val stateMachine = getStateMachine(paymentId)
        stateMachine.stop()
        stateMachine.start()
        logger.debug("Reset state machine for payment: $paymentId")
    }

    fun isInFinalState(paymentId: UUID): Boolean {
        return isFinalState(getCurrentState(paymentId))
    }

    private fun isFinalState(state: PaymentState): Boolean {
        return state == PaymentState.COMPLETED ||
                state == PaymentState.FAILED ||
                state == PaymentState.COMPENSATED ||
                state == PaymentState.CANCELLED
    }

    fun getStateMachineHistory(paymentId: UUID): List<PaymentState> {
        val stateMachine = getStateMachine(paymentId)
        // In a real implementation, you might want to track state transitions
        // For now, return current state
        return listOf(stateMachine.state.id)
    }

}
