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

    /**
     * Start the FraudDetection workflow for the event with @param(depositEvent)
     *
     * @param depositEvent The deposit event for which the fraud detection workflow needs to be started
     * @throws JsonProcessingException This exception is thrown if there is any anomaly in objectMapper.writeValueAsString
     */
    public void startWorkflow(DepositEvent depositEvent) throws JsonProcessingException {
        log.info("[{}] starting workflow...", depositEvent.getEventId());

        // Start the workflow statemachine for the depositEvent
        sendSMEventNUpdateDepositEvent(depositEvent, FraudEvent.START);

        log.info("[{}] sending CONSORTIUM_SERVICE_CMD, IMAGE_SERVICE_CMD, RULE_SERVICE_CMD Kafka commands", depositEvent.getEventId());
        String requestString = objectMapper.writeValueAsString(depositEvent);

        // Send kafka message to Consortium, Image and Rule service so that they can start their processing in parallel
        kafkaTemplate.send(CONSORTIUM_SERVICE_CMD, requestString);
        kafkaTemplate.send(IMAGE_SERVICE_CMD, requestString);
        kafkaTemplate.send(RULE_SERVICE_CMD, requestString);
    }

    /**
     * Consortium service sends kafka message to trigger this function, this function updates the workflow state and
     * workflow result, then forwards the statemachine to next state if both Consortium and Image service is completed.
     * This function marks the statemachine state as CONSORTIUM_RESPONSE_RECEIVED
     *
     * @param consortiumServiceResponse The result of the Consortium Service, this comes through Kafka message from
     *                                  Consortium Service
     * @throws JsonProcessingException This exception is thrown if there is any anomaly in objectMapper.writeValueAsString
     */
    @Transactional
    public void handleConsortiumServiceResponse(ConsortiumServiceResponse consortiumServiceResponse)
            throws JsonProcessingException {
        log.info("[{}] handleConsortiumServiceResponse called with response = {}", consortiumServiceResponse.getEventId(),
                consortiumServiceResponse);

        // Update workflow state and workflow result
        FraudDetectionWorkflowEntity workflow = fraudDetectionWorkflowRepo.findById(consortiumServiceResponse.getEventId()).orElseThrow();
        workflow.setConsortiumCompleted(true);
        workflow.setConsortiumResult(consortiumServiceResponse.getResult());
        fraudDetectionWorkflowRepo.save(workflow);

        // Forward to next state
        DepositEvent depositEvent = depositEventRepository.findByEventId(consortiumServiceResponse.getEventId()).getFirst();
        sendSMEventNUpdateDepositEvent(depositEvent, FraudEvent.CONSORTIUM_RESPONSE_RECEIVED);

        // Check if Consortium and Image service completed, if yes then trigger ML service
        checkConsortiumNImageServiceCompletionToTriggerML(depositEvent, workflow);
    }

    /**
     * Image service sends kafka message to trigger this function, this function updates the workflow state and
     * workflow result, then forwards the statemachine to next state if both Consortium and Image service is completed.
     * This function marks the statemachine state as IMAGE_RESPONSE_RECEIVED
     *
     * @param imageServiceResponse The result of the Image Service, this comes through Kafka message from
     *                             Image Service
     * @throws JsonProcessingException This exception is thrown if there is any anomaly in objectMapper.writeValueAsString
     */
    @Transactional
    public void handleImageServiceResponse(ImageServiceResponse imageServiceResponse) throws JsonProcessingException {
        log.info("[{}] handleImageServiceResponse called with response = {}", imageServiceResponse.getEventId(),
                imageServiceResponse);

        // Update workflow state and workflow result
        FraudDetectionWorkflowEntity workflow = fraudDetectionWorkflowRepo.findById(imageServiceResponse.getEventId()).orElseThrow();
        workflow.setImageCompleted(true);
        workflow.setImageResult(imageServiceResponse.getResult());
        fraudDetectionWorkflowRepo.save(workflow);

        // Forward to next state
        DepositEvent depositEvent = depositEventRepository.findByEventId(imageServiceResponse.getEventId()).getFirst();
        sendSMEventNUpdateDepositEvent(depositEvent, FraudEvent.IMAGE_RESPONSE_RECEIVED);

        // Check if Consortium and Image service completed, if yes then trigger ML service
        checkConsortiumNImageServiceCompletionToTriggerML(depositEvent, workflow);
    }

    /**
     * Rule service sends kafka message to trigger this function, this function updates the workflow state and
     * workflow result. This function marks the statemachine state as RULE_RESPONSE_RECEIVED
     *
     * @param ruleServiceResponse The result of the Rule Service, this comes through Kafka message from
     *                            Rule Service
     */
    @Transactional
    public void handleRuleServiceResponse(RuleServiceResponse ruleServiceResponse) {
        log.info("[{}] handleRuleServiceResponse called with response = {}", ruleServiceResponse.getEventId(),
                ruleServiceResponse);

        // Update workflow state and workflow result
        FraudDetectionWorkflowEntity workflow = fraudDetectionWorkflowRepo.findById(ruleServiceResponse.getEventId()).orElseThrow();
        workflow.setRuleCompleted(true);
        workflow.setRuleResult(ruleServiceResponse.getResult());
        fraudDetectionWorkflowRepo.save(workflow);

        // Mark Rule service completion
        DepositEvent depositEvent = depositEventRepository.findByEventId(ruleServiceResponse.getEventId()).getFirst();
        sendSMEventNUpdateDepositEvent(depositEvent, FraudEvent.RULE_RESPONSE_RECEIVED);

        // Check if ML and Rule service completed, if yes then trigger all service completion state
        tryComplete(depositEvent, workflow);
    }

    /**
     * ML service sends kafka message to trigger this function, this function updates the workflow state and
     * workflow result, then forwards the statemachine to next state if both ML and Rule service is completed.
     * This function marks the statemachine state as ML_RESPONSE_RECEIVED
     *
     * @param mlServiceResponse The result of the ML Service, this comes through Kafka message from
     *                          ML Service
     */
    @Transactional
    public void handleMLServiceResponse(MLServiceResponse mlServiceResponse) {
        log.info("[{}] handleMLServiceResponse called with response = {}", mlServiceResponse.getEventId(), mlServiceResponse);

        // Update workflow state and workflow result
        FraudDetectionWorkflowEntity workflow = fraudDetectionWorkflowRepo.findById(mlServiceResponse.getEventId()).orElseThrow();
        workflow.setMlCompleted(true);
        workflow.setMlResult(mlServiceResponse.getResult());
        fraudDetectionWorkflowRepo.save(workflow);

        // Forward to next state
        DepositEvent depositEvent = depositEventRepository.findByEventId(mlServiceResponse.getEventId()).getFirst();
        sendSMEventNUpdateDepositEvent(depositEvent, FraudEvent.ML_RESPONSE_RECEIVED);

        // Check if ML and Rule service completed, if yes then trigger all service completion state
        tryComplete(depositEvent, workflow);
    }

    /**
     * Checks if both Consortium and Image service completed, if completed then trigger the ML service
     *
     * @param depositEvent The depositEvent for which ML service should be triggered
     * @param workflow     The workflow state of the current statemachine which refers to the depositEvent
     * @throws JsonProcessingException This exception is thrown if there is any anomaly in objectMapper.writeValueAsString
     */
    private void checkConsortiumNImageServiceCompletionToTriggerML(DepositEvent depositEvent, FraudDetectionWorkflowEntity workflow) throws JsonProcessingException {
        if (workflow.isConsortiumCompleted() && workflow.isImageCompleted()) {
            log.info("[{}] both Consortium and Image service completed for this event, so triggering ML service", depositEvent.getEventId());
            triggerML(depositEvent, workflow);
        }
    }

    /**
     * Trigger the ML service by preparing the MLServiceRequest. It sends the Kafka message to ML Service to perform its
     * function based on the Consortium Service Response and Image Service Response
     *
     * @param depositEvent The depositEvent for which ML service should be triggered
     * @param workflow     The workflow state of the current statemachine which refers to the depositEvent
     * @throws JsonProcessingException This exception is thrown if there is any anomaly in objectMapper.writeValueAsString
     */
    private void triggerML(DepositEvent depositEvent, FraudDetectionWorkflowEntity workflow) throws JsonProcessingException {
        sendSMEventNUpdateDepositEvent(depositEvent, FraudEvent.BOTH_DEPENDENCIES_READY);

        MLServiceRequest request = mlServiceRequestDataPreparer.prepareMLServiceRequest(workflow, depositEvent.getEventId());
        String requestString = objectMapper.writeValueAsString(request);
        kafkaTemplate.send(ML_SERVICE_CMD, requestString);
    }

    /**
     * Checks if both ML and Rule service completed, if completed then trigger the ALL_COMPLETED machine state
     *
     * @param depositEvent The depositEvent for which ML service should be triggered
     * @param workflow     The workflow state of the current statemachine which refers to the depositEvent
     */
    private void tryComplete(DepositEvent depositEvent, FraudDetectionWorkflowEntity workflow) {
        if (workflow.isMlCompleted() && workflow.isRuleCompleted()) {
            log.info("[{}] both ML and Rule service completed for this event, so triggering ALL_COMPLETED StateMachine state",
                    depositEvent.getEventId());
            sendSMEventNUpdateDepositEvent(depositEvent, FraudEvent.ALL_COMPLETED);
        }
    }

    /**
     * Send event to the StateMachine, so that it can decide it's next state
     *
     * @param depositEvent The depositEvent for which ML service should be triggered
     * @param fraudEvent   Event of the StateMachine which determines the next state of the StateMachine
     */
    private void sendSMEventNUpdateDepositEvent(DepositEvent depositEvent, FraudEvent fraudEvent) {
        StateMachine<WorkflowState, FraudEvent> sm = buildStateMachineForWorkflow(
                depositEvent.getEventId(),
                depositEvent.getWorkflow());
        sm.sendEvent(Mono.just(MessageBuilder.withPayload(fraudEvent).build())).blockLast();
        depositEvent.setWorkflow(sm.getState().getId());
        depositEventRepository.save(depositEvent);
    }

    /**
     * Creates a new StateMachine or returns an already created StateMachine reference with the machineId (which is the
     * eventId referenced by the first parameter of the function
     *
     * @param eventId      The UUID of the depositEvent
     * @param currentState Current state of the StateMachine
     * @return Reference to the newly created or already existing StateMachine
     */
    private StateMachine<WorkflowState, FraudEvent> buildStateMachineForWorkflow(UUID eventId, WorkflowState currentState) {
        final String machineId = eventId.toString();
        StateMachine<WorkflowState, FraudEvent> sm = stateMachineFactory.getStateMachine(machineId);

        if (sm.getState() == null) {
            try {
                sm.stopReactively().block();
            } catch (Exception e) {
                log.warn("Error stopping state machine {}", machineId, e);
            }

            sm.getStateMachineAccessor().doWithAllRegions(access ->
                    access.resetStateMachineReactively(new DefaultStateMachineContext<>(
                            currentState,    // state
                            null,            // event
                            null,            // message
                            null,            // extended state
                            null,            // variables
                            machineId        // state machine id - important!
                    )).block());

            try {
                sm.startReactively().block();
            } catch (Exception e) {
                log.warn("Error starting state machine {}", machineId, e);
            }
        }

        return sm;
    }
}
