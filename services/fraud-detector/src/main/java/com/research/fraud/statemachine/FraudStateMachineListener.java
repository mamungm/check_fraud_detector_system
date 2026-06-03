package com.research.fraud.statemachine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FraudStateMachineListener extends StateMachineListenerAdapter<WorkflowState, FraudEvent> {
    @Override
    public void stateContext(
            StateContext<WorkflowState, FraudEvent> context) {
        if (context.getStage() != StateContext.Stage.STATE_CHANGED) {
            return;
        }
        String machineId = context.getStateMachine().getId();

        WorkflowState source = context.getSource() != null
                ? context.getSource().getId()
                : null;

        WorkflowState target = context.getTarget() != null
                ? context.getTarget().getId()
                : null;

        if (source == null && target == null) {
            return;
        }

        log.info("[{}] STATE CHANGED: {} -> {}", machineId, source, target);
    }
}
