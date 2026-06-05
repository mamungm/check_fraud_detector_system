package com.research.fraud.statemachine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.research.fraud.db.entity.DepositEvent;
import com.research.fraud.db.repo.DepositEventRepository;
import com.research.fraud.dto.MLServiceRequest;
import com.research.fraud.service.MLServiceRequestDataPreparer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.state.State;
import reactor.core.publisher.Flux;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FraudWorkflowServiceTest {
    @Mock
    KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    StateMachineFactory<WorkflowState, FraudEvent> stateMachineFactory;

    @Mock
    FraudDetectionWorkflowRepo fraudDetectionWorkflowRepo;

    @Mock
    MLServiceRequestDataPreparer mlServiceRequestDataPreparer;

    @Mock
    DepositEventRepository depositEventRepository;

    ObjectMapper objectMapper;

    FraudWorkflowService fraudWorkflowService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        fraudWorkflowService = new FraudWorkflowService(
                kafkaTemplate,
                stateMachineFactory,
                fraudDetectionWorkflowRepo,
                mlServiceRequestDataPreparer,
                depositEventRepository,
                objectMapper
        );
    }

    @Test
    void startWorkflow_sendsThreeKafkaMessagesAndSavesDeposit() throws Exception {
        // prepare deposit event
        DepositEvent depositEvent = DepositEvent.builder()
                .amount(new BigDecimal("100.00"))
                .depositTimestamp(OffsetDateTime.now())
                .workflow(WorkflowState.RECEIVED)
                .build();
        UUID id = UUID.randomUUID();
        depositEvent.setEventId(id);

        // mock state machine behavior
        @SuppressWarnings("unchecked")
        StateMachine<WorkflowState, FraudEvent> sm = mock(StateMachine.class);
        State<WorkflowState, FraudEvent> state = mock(State.class);
        when(state.getId()).thenReturn(WorkflowState.RECEIVED);
        when(sm.getState()).thenReturn(state);
        when(sm.sendEvent(any(reactor.core.publisher.Mono.class))).thenReturn(Flux.empty());
        when(stateMachineFactory.getStateMachine(id.toString())).thenReturn(sm);

        // stub kafka sends to avoid NPE
        when(kafkaTemplate.send(anyString(), any())).thenReturn(null);

        // call
        fraudWorkflowService.startWorkflow(depositEvent);

        // verify three kafka sends
        verify(kafkaTemplate, times(1)).send(eq("Consortium_Service_CMD"), anyString());
        verify(kafkaTemplate, times(1)).send(eq("Image_Service_CMD"), anyString());
        verify(kafkaTemplate, times(1)).send(eq("Rule_Service_CMD"), anyString());

        // depositEvent should be saved via depositEventRepository.save when state machine event was sent
        verify(depositEventRepository, atLeastOnce()).save(any(DepositEvent.class));
    }

    @Test
    void triggerML_preparesRequestAndSendsKafka() throws Exception {
        // Create deposit + workflow
        DepositEvent depositEvent = DepositEvent.builder()
                .amount(new BigDecimal("5.00"))
                .depositTimestamp(OffsetDateTime.now())
                .workflow(WorkflowState.WAITING_FOR_DEPENDENCIES)
                .build();
        UUID id = UUID.randomUUID();
        depositEvent.setEventId(id);

        FraudDetectionWorkflowEntity workflow = FraudDetectionWorkflowEntity.builder()
                .eventId(id)
                .consortiumCompleted(true)
                .imageCompleted(true)
                .build();

        // state machine mocks (sendSMEventNUpdateDepositEvent will use them)
        @SuppressWarnings("unchecked")
        StateMachine<WorkflowState, FraudEvent> sm = mock(StateMachine.class);
        State<WorkflowState, FraudEvent> state = mock(State.class);
        when(state.getId()).thenReturn(WorkflowState.ML_ANALYSIS_PENDING);
        when(sm.getState()).thenReturn(state);
        when(sm.sendEvent(any(reactor.core.publisher.Mono.class))).thenReturn(Flux.empty());
        when(stateMachineFactory.getStateMachine(id.toString())).thenReturn(sm);

        // ML request preparer
        MLServiceRequest mlRequest = MLServiceRequest.builder().build();
        when(mlServiceRequestDataPreparer.prepareMLServiceRequest(eq(workflow), eq(id))).thenReturn(mlRequest);

        when(kafkaTemplate.send(anyString(), any())).thenReturn(null);

        // invoke private method triggerML via reflection
        Method triggerML = FraudWorkflowService.class.getDeclaredMethod("triggerML", DepositEvent.class, FraudDetectionWorkflowEntity.class);
        triggerML.setAccessible(true);
        triggerML.invoke(fraudWorkflowService, depositEvent, workflow);

        // verify ml preparer called
        verify(mlServiceRequestDataPreparer, times(1)).prepareMLServiceRequest(eq(workflow), eq(id));

        // verify kafka send to ML topic
        verify(kafkaTemplate, times(1)).send(eq("ML_Service_CMD"), anyString());

        // deposit event saved as part of state update
        verify(depositEventRepository, atLeastOnce()).save(any(DepositEvent.class));
    }

    @Test
    void tryComplete_whenBothMlAndRuleCompleted_triggersAllCompletedState() throws Exception {
        DepositEvent depositEvent = DepositEvent.builder()
                .amount(new BigDecimal("10.00"))
                .depositTimestamp(OffsetDateTime.now())
                .workflow(WorkflowState.ML_ANALYSIS_PENDING)
                .build();
        UUID id = UUID.randomUUID();
        depositEvent.setEventId(id);

        FraudDetectionWorkflowEntity workflow = FraudDetectionWorkflowEntity.builder()
                .eventId(id)
                .mlCompleted(true)
                .ruleCompleted(true)
                .build();

        @SuppressWarnings("unchecked")
        StateMachine<WorkflowState, FraudEvent> sm = mock(StateMachine.class);
        State<WorkflowState, FraudEvent> state = mock(State.class);
        when(state.getId()).thenReturn(WorkflowState.COMPLETED);
        when(sm.getState()).thenReturn(state);
        when(sm.sendEvent(any(reactor.core.publisher.Mono.class))).thenReturn(Flux.empty());
        when(stateMachineFactory.getStateMachine(id.toString())).thenReturn(sm);

        // invoke private tryComplete
        Method tryComplete = FraudWorkflowService.class.getDeclaredMethod("tryComplete", DepositEvent.class, FraudDetectionWorkflowEntity.class);
        tryComplete.setAccessible(true);
        tryComplete.invoke(fraudWorkflowService, depositEvent, workflow);

        // verify deposit event saved when machine transitioned to ALL_COMPLETED
        verify(depositEventRepository, atLeastOnce()).save(any(DepositEvent.class));
    }
}
