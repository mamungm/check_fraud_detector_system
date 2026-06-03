package com.research.fraud.statemachine;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;

@Configuration
@EnableStateMachineFactory
@RequiredArgsConstructor
public class FraudStateMachineConfig extends EnumStateMachineConfigurerAdapter<WorkflowState, FraudEvent> {
    @Override
    public void configure(StateMachineStateConfigurer<WorkflowState, FraudEvent> states) throws Exception {
        states.withStates()
                .initial(WorkflowState.RECEIVED)
                .state(WorkflowState.WAITING_FOR_DEPENDENCIES)
                .state(WorkflowState.FEATURE_ENGINEERING)
                .state(WorkflowState.ML_ANALYSIS_PENDING)
                .state(WorkflowState.FINALIZING)
                .end(WorkflowState.COMPLETED)
                .end(WorkflowState.FAILED);
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<WorkflowState, FraudEvent> transitions) throws Exception {
        transitions
                .withExternal()
                .source(WorkflowState.RECEIVED)
                .target(WorkflowState.WAITING_FOR_DEPENDENCIES)
                .event(FraudEvent.START)

                .and()

                .withExternal()
                .source(WorkflowState.WAITING_FOR_DEPENDENCIES)
                .target(WorkflowState.ML_ANALYSIS_PENDING)
                .event(FraudEvent.BOTH_DEPENDENCIES_READY)

                .and()

                .withExternal()
                .source(WorkflowState.ML_ANALYSIS_PENDING)
                .target(WorkflowState.FINALIZING)
                .event(FraudEvent.ML_RESPONSE_RECEIVED)

                .and()

                .withExternal()
                .source(WorkflowState.FINALIZING)
                .target(WorkflowState.COMPLETED)
                .event(FraudEvent.ALL_COMPLETED)

                .and()

                .withExternal()
                .source(WorkflowState.RECEIVED)
                .target(WorkflowState.FAILED)
                .event(FraudEvent.FAILURE);
    }
}
