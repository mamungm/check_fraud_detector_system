package com.research.fraud.statemachine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.research.fraud.config.Constants;
import com.research.fraud.db.entity.DepositEvent;
import com.research.fraud.db.repo.DepositEventRepository;
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

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FraudWorkflowService implements Constants {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final StateMachineFactory<WorkflowState, FraudEvent> stateMachineFactory;
    private final FraudDetectionWorkflowRepo fraudDetectionWorkflowRepo;
    private final MLServiceRequestDataPreparer mlServiceRequestDataPreparer;
    private final DepositEventRepository depositEventRepository;
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

    private void sendSMEventNUpdateDepositEvent(DepositEvent depositEvent, FraudEvent fraudEvent) {
        StateMachine<WorkflowState, FraudEvent> sm = buildStateMachineForWorkflow(
                depositEvent.getEventId(),
                depositEvent.getWorkflow());
        sm.sendEvent(Mono.just(MessageBuilder.withPayload(fraudEvent).build())).blockLast();
        depositEvent.setWorkflow(sm.getState().getId());
        depositEventRepository.save(depositEvent);
    }

    public void startWorkflow(DepositEvent depositEvent) throws JsonProcessingException {
        log.info("starting workflow...");

        sendSMEventNUpdateDepositEvent(depositEvent, FraudEvent.START);

        log.info("sending CONSORTIUM_SERVICE_CMD, IMAGE_SERVICE_CMD, RULE_SERVICE_CMD kafka commands");
        String requestString = objectMapper.writeValueAsString(depositEvent);
        kafkaTemplate.send(CONSORTIUM_SERVICE_CMD, requestString);
        kafkaTemplate.send(IMAGE_SERVICE_CMD, requestString);
        kafkaTemplate.send(RULE_SERVICE_CMD, requestString);
    }

    @Transactional
    public void handleConsortiumServiceResponse(ConsortiumServiceResponse response) throws JsonProcessingException {
        log.info("handleConsortiumServiceResponse called with response = {}", response);

        FraudDetectionWorkflowEntity workflow = fraudDetectionWorkflowRepo.findById(response.getEventId()).orElseThrow();
        workflow.setConsortiumCompleted(true);
        workflow.setConsortiumResult(response.getResult());
        fraudDetectionWorkflowRepo.save(workflow);

        DepositEvent depositEvent = depositEventRepository.findByEventId(response.getEventId()).getFirst();
        sendSMEventNUpdateDepositEvent(depositEvent, FraudEvent.CONSORTIUM_RESPONSE_RECEIVED);

        checkConsortiumNImageServiceCompletionToTriggerML(depositEvent, workflow, response.getEventId());
    }

    @Transactional
    public void handleImageServiceResponse(ImageServiceResponse response) throws JsonProcessingException {
        log.info("handleImageServiceResponse called with response = {}", response);

        FraudDetectionWorkflowEntity workflow = fraudDetectionWorkflowRepo.findById(response.getEventId()).orElseThrow();
        workflow.setImageCompleted(true);
        workflow.setImageResult(response.getResult());
        fraudDetectionWorkflowRepo.save(workflow);

        DepositEvent depositEvent = depositEventRepository.findByEventId(response.getEventId()).getFirst();
        sendSMEventNUpdateDepositEvent(depositEvent, FraudEvent.IMAGE_RESPONSE_RECEIVED);

        checkConsortiumNImageServiceCompletionToTriggerML(depositEvent, workflow, response.getEventId());
    }

    @Transactional
    public void handleRuleServiceResponse(RuleServiceResponse response) {
        log.info("handleRuleServiceResponse called with response = {}", response);

        FraudDetectionWorkflowEntity workflow = fraudDetectionWorkflowRepo.findById(response.getEventId()).orElseThrow();
        workflow.setRuleCompleted(true);
        workflow.setRuleResult(response.getResult());
        fraudDetectionWorkflowRepo.save(workflow);

        DepositEvent depositEvent = depositEventRepository.findByEventId(response.getEventId()).getFirst();
        sendSMEventNUpdateDepositEvent(depositEvent, FraudEvent.RULE_RESPONSE_RECEIVED);
    }

    @Transactional
    public void handleMLServiceResponse(MLServiceResponse response) {
        log.info("handleMLServiceResponse called with response = {}", response);

        FraudDetectionWorkflowEntity workflow = fraudDetectionWorkflowRepo.findById(response.getEventId()).orElseThrow();
        workflow.setMlCompleted(true);
        workflow.setMlResult(response.getResult());
        fraudDetectionWorkflowRepo.save(workflow);

        DepositEvent depositEvent = depositEventRepository.findByEventId(response.getEventId()).getFirst();
        sendSMEventNUpdateDepositEvent(depositEvent, FraudEvent.ML_RESPONSE_RECEIVED);

        tryComplete(depositEvent, workflow);
    }

    private void checkConsortiumNImageServiceCompletionToTriggerML(DepositEvent depositEvent, FraudDetectionWorkflowEntity workflow, UUID eventId) throws JsonProcessingException {
        if (workflow.isConsortiumCompleted() && workflow.isImageCompleted()) {
            triggerML(depositEvent, workflow, eventId);
        }
    }

    private void triggerML(DepositEvent depositEvent, FraudDetectionWorkflowEntity workflow, UUID eventId) throws JsonProcessingException {
        sendSMEventNUpdateDepositEvent(depositEvent, FraudEvent.BOTH_DEPENDENCIES_READY);

        MLServiceRequest request = mlServiceRequestDataPreparer.prepareMLServiceRequest(workflow, eventId);
        String requestString = objectMapper.writeValueAsString(request);
        kafkaTemplate.send(ML_SERVICE_CMD, requestString);
    }

    private void tryComplete(DepositEvent depositEvent, FraudDetectionWorkflowEntity workflow) {
        if (workflow.isMlCompleted() && workflow.isRuleCompleted()) {
            sendSMEventNUpdateDepositEvent(depositEvent, FraudEvent.ALL_COMPLETED);
        }
    }
}
