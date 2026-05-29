package com.research.fraud.statemachine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.research.fraud.config.Constants;
import com.research.fraud.db.entity.DepositEvent;
import com.research.fraud.dto.*;
import com.research.fraud.service.MLServiceRequestDataPreparer;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FraudWorkflowService implements Constants {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final StateMachineFactory<WorkflowState, FraudEvent> stateMachineFactory;
    private final FraudDetectionWorkflowRepo fraudDetectionWorkflowRepo;
    private final MLServiceRequestDataPreparer mlServiceRequestDataPreparer;
    private final ObjectMapper objectMapper;

    private StateMachine<WorkflowState, FraudEvent> buildStateMachineForWorkflow(UUID eventId, WorkflowState currentState) {
        final String machineId = eventId.toString();
        StateMachine<WorkflowState, FraudEvent> sm = stateMachineFactory.getStateMachine(machineId);

        if (sm.getState() == null) {
            try {
                sm.stopReactively().block();
            } catch (Exception e) {
                log.warn("Error stopping state machine {}", machineId, e);
            }

            sm.getStateMachineAccessor().doWithAllRegions(access -> {
                access.resetStateMachineReactively(new DefaultStateMachineContext<>(
                        currentState,    // state
                        null,            // event
                        null,            // message
                        null,            // extended state
                        null,            // variables
                        machineId        // state machine id - important!
                )).block();
            });

            try {
                sm.startReactively().block();
            } catch (Exception e) {
                log.warn("Error starting state machine {}", machineId, e);
            }
        }

        return sm;
    }

    public void startWorkflow(DepositEvent request) throws JsonProcessingException {
        log.info("starting workflow...");
        FraudDetectionWorkflowEntity workflow = request.getWorkflow();

        StateMachine<WorkflowState, FraudEvent> sm = buildStateMachineForWorkflow(
                workflow.getEventId(),
                workflow.getState());
        sm.sendEvent(Mono.just(MessageBuilder.withPayload(FraudEvent.START).build())).blockLast();
        log.info("sm = {}", sm);

        log.info("sending CONSORTIUM_SERVICE_CMD, IMAGE_SERVICE_CMD, RULE_SERVICE_CMD kafka commands");
        String requestString = objectMapper.writeValueAsString(request);
        kafkaTemplate.send(CONSORTIUM_SERVICE_CMD, requestString);
        kafkaTemplate.send(IMAGE_SERVICE_CMD, requestString);
        kafkaTemplate.send(RULE_SERVICE_CMD, requestString);

        workflow.setState(WorkflowState.WAITING_FOR_DEPENDENCIES);
        workflow.setUpdatedAt(Instant.now());
        log.info("updated workflow = " + workflow);
        fraudDetectionWorkflowRepo.save(workflow);
    }

    @Transactional
    public void handleConsortiumServiceResponse(ConsortiumServiceResponse response) throws JsonProcessingException {
        log.info("handleConsortiumServiceResponse called");
        FraudDetectionWorkflowEntity workflow = fraudDetectionWorkflowRepo.findById(response.getEventId()).orElseThrow();

        workflow.setConsortiumCompleted(true);
        workflow.setConsortiumResult(response.getResult());

        StateMachine<WorkflowState, FraudEvent> sm = buildStateMachineForWorkflow(
                workflow.getEventId(),
                workflow.getState());
        log.info("sm = {}", sm);
        sm.sendEvent(Mono.just(MessageBuilder.withPayload(FraudEvent.CONSORTIUM_RESPONSE_RECEIVED).build())).blockLast();
        workflow.setState(sm.getState().getId());
        fraudDetectionWorkflowRepo.save(workflow);
        checkConsortiumNImageServiceCompletionToTriggerML(workflow);
    }

    @Transactional
    public void handleImageServiceResponse(ImageServiceResponse response) throws JsonProcessingException {
        FraudDetectionWorkflowEntity workflow = fraudDetectionWorkflowRepo.findById(response.getEventId()).orElseThrow();

        workflow.setImageCompleted(true);
        workflow.setImageResult(response.getResult());

        StateMachine<WorkflowState, FraudEvent> sm = buildStateMachineForWorkflow(
                workflow.getEventId(),
                workflow.getState());
        sm.sendEvent(Mono.just(MessageBuilder.withPayload(FraudEvent.IMAGE_RESPONSE_RECEIVED).build())).blockLast();
        workflow.setState(sm.getState().getId());
        fraudDetectionWorkflowRepo.save(workflow);
        checkConsortiumNImageServiceCompletionToTriggerML(workflow);
    }

    @Transactional
    public void handleRuleServiceResponse(RuleServiceResponse response) {
        FraudDetectionWorkflowEntity workflow = fraudDetectionWorkflowRepo.findById(response.getEventId()).orElseThrow();

        workflow.setRuleCompleted(true);
        workflow.setRuleResult(response.getResult());

        StateMachine<WorkflowState, FraudEvent> sm = buildStateMachineForWorkflow(
                workflow.getEventId(),
                workflow.getState());
        sm.sendEvent(Mono.just(MessageBuilder.withPayload(FraudEvent.RULE_RESPONSE_RECEIVED).build())).blockLast();
        workflow.setState(sm.getState().getId());
        fraudDetectionWorkflowRepo.save(workflow);
    }

    @Transactional
    public void handleMLServiceResponse(MLServiceResponse response) {
        FraudDetectionWorkflowEntity workflow = fraudDetectionWorkflowRepo.findById(response.getEventId()).orElseThrow();

        workflow.setRuleCompleted(true);
        workflow.setRuleResult(response.getResult());

        StateMachine<WorkflowState, FraudEvent> sm = buildStateMachineForWorkflow(
                workflow.getEventId(),
                workflow.getState());
        sm.sendEvent(Mono.just(MessageBuilder.withPayload(FraudEvent.ML_RESPONSE_RECEIVED).build())).blockLast();
        workflow.setState(sm.getState().getId());
        fraudDetectionWorkflowRepo.save(workflow);
        tryComplete(workflow);
    }

    private void checkConsortiumNImageServiceCompletionToTriggerML(FraudDetectionWorkflowEntity workflow) throws JsonProcessingException {
        if (workflow.isConsortiumCompleted() && workflow.isImageCompleted()) {
            triggerML(workflow);
        }
    }

    private void triggerML(FraudDetectionWorkflowEntity workflow) throws JsonProcessingException {
        MLServiceRequest request = mlServiceRequestDataPreparer.prepareMLServiceRequest(workflow);

        StateMachine<WorkflowState, FraudEvent> sm = buildStateMachineForWorkflow(
                workflow.getEventId(),
                workflow.getState());
        log.info("sm = {}", sm);
        sm.sendEvent(Mono.just(MessageBuilder.withPayload(FraudEvent.BOTH_DEPENDENCIES_READY).build())).blockLast();

        String requestString = objectMapper.writeValueAsString(request);
        kafkaTemplate.send(ML_SERVICE_CMD, requestString);

        workflow.setState(WorkflowState.ML_ANALYSIS_PENDING);
    }

    private void tryComplete(FraudDetectionWorkflowEntity workflow) {
        if (workflow.isMlCompleted() && workflow.isRuleCompleted()) {

            workflow.setState(WorkflowState.COMPLETED);

//            websocketTemplate.convertAndSend(
//                    "/topic/fraud/" + workflow.getWorkflowId(),
//                    buildFinalResponse(workflow));
        }
    }
}
