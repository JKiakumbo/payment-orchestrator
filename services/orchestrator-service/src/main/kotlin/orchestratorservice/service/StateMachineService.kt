package dev.jkiakumbo.paymentorchestrator.orchestratorservice.service

import dev.jkiakumbo.paymentorchestrator.orchestratorservice.state.PaymentEvent
import dev.jkiakumbo.paymentorchestrator.orchestratorservice.state.PaymentState
import org.slf4j.LoggerFactory
import org.springframework.statemachine.StateMachine
import org.springframework.statemachine.service.StateMachineService
import org.springframework.stereotype.Service
import java.util.*

@Service
class StateMachineService(
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
        if (!stateMachine.isRunning) {
            stateMachine.start()
            logger.debug("Started state machine for payment: $paymentId")
        }
    }

    fun stopStateMachine(paymentId: UUID) {
        val stateMachine = getStateMachine(paymentId)
        if (stateMachine.isRunning) {
            stateMachine.stop()
            logger.debug("Stopped state machine for payment: $paymentId")
        }
    }
}