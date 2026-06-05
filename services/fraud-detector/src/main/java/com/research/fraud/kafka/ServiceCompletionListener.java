package com.research.fraud.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.research.fraud.dto.*;
import com.research.fraud.service.EventWSSessionMapper;
import com.research.fraud.service.SingleServiceCompletionResponsePreparer;
import com.research.fraud.statemachine.FraudWorkflowService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import static com.research.fraud.config.Constants.SINGLE_SERVICE_COMPLETION_TOPIC;

@Component
@AllArgsConstructor
@Slf4j
public class ServiceCompletionListener {
    private final ObjectMapper objectMapper;
    private final FraudWorkflowService fraudWorkflowService;
    private final EventWSSessionMapper eventWSSessionMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final SingleServiceCompletionResponsePreparer singleServiceCompletionResponsePreparer;

    @KafkaListener(
            topics = "Consortium_Service_Response",
            groupId = "${fraud.kafka.consumer-group:fraud-detector-completions}"
    )
    public void onConsortiumServiceResponseMessage(String payload) {
        try {
            log.info("Received consortium service completion event {}", payload);
            ServiceCompletionEvent evt = objectMapper.readValue(payload, ServiceCompletionEvent.class);
            fraudWorkflowService.handleConsortiumServiceResponse(
                    ConsortiumServiceResponse.builder()
                            .eventId(evt.eventId())
                            .result(payload)
                            .build());
            String wsSessionId = eventWSSessionMapper.getWSSessionIdFromEventId(evt.eventId());
            log.info("WS Session Id: {}", wsSessionId);

            messagingTemplate.convertAndSend(
                    SINGLE_SERVICE_COMPLETION_TOPIC,
                    singleServiceCompletionResponsePreparer.prepareConsortiumServiceResponseMessage(evt)
            );
        } catch (Exception e) {
            log.warn("Failed to parse service completion message: {}", payload, e);
        }
    }

    @KafkaListener(
            topics = "Image_Service_Response",
            groupId = "${fraud.kafka.consumer-group:fraud-detector-completions}"
    )
    public void onImageServiceResponseMessage(String payload) {
        try {
            log.info("Received image service completion event from topic {}", payload);
            ServiceCompletionEvent evt = objectMapper.readValue(payload, ServiceCompletionEvent.class);
            fraudWorkflowService.handleImageServiceResponse(ImageServiceResponse.builder()
                    .eventId(evt.eventId())
                    .result(payload)
                    .build());
            String wsSessionId = eventWSSessionMapper.getWSSessionIdFromEventId(evt.eventId());
            log.info("WS Session Id: {}", wsSessionId);

            messagingTemplate.convertAndSend(
                    SINGLE_SERVICE_COMPLETION_TOPIC,
                    singleServiceCompletionResponsePreparer.prepareImageServiceResponseMessage(evt)
            );
        } catch (Exception e) {
            log.warn("Failed to parse service completion message: {}", payload, e);
        }
    }

    @KafkaListener(
            topics = "Rule_Service_Response",
            groupId = "${fraud.kafka.consumer-group:fraud-detector-completions}"
    )
    public void onRuleServiceResponseMessage(String payload) {
        try {
            log.info("Received rule service completion event from topic {}", payload);
            ServiceCompletionEvent evt = objectMapper.readValue(payload, ServiceCompletionEvent.class);
            fraudWorkflowService.handleRuleServiceResponse(RuleServiceResponse.builder()
                    .eventId(evt.eventId())
                    .result(payload)
                    .build());
            String wsSessionId = eventWSSessionMapper.getWSSessionIdFromEventId(evt.eventId());
            log.info("WS Session Id: {}", wsSessionId);

            messagingTemplate.convertAndSend(
                    SINGLE_SERVICE_COMPLETION_TOPIC,
                    singleServiceCompletionResponsePreparer.prepareRuleServiceResponseMessage(evt)
            );
        } catch (Exception e) {
            log.warn("Failed to parse service completion message: {}", payload, e);
        }
    }

    @KafkaListener(
            topics = "ML_Service_Response",
            groupId = "${fraud.kafka.consumer-group:fraud-detector-completions}"
    )
    public void onMLServiceResponseMessage(String payload) {
        try {
            log.info("Received ML service completion event from topic {}", payload);
            ServiceCompletionEvent evt = objectMapper.readValue(payload, ServiceCompletionEvent.class);
            fraudWorkflowService.handleMLServiceResponse(MLServiceResponse.builder()
                    .eventId(evt.eventId())
                    .result(payload)
                    .build());
            String wsSessionId = eventWSSessionMapper.getWSSessionIdFromEventId(evt.eventId());
            log.info("WS Session Id: {}", wsSessionId);

            messagingTemplate.convertAndSend(
                    SINGLE_SERVICE_COMPLETION_TOPIC,
                    singleServiceCompletionResponsePreparer.prepareMLServiceResponseMessage(evt)
            );
        } catch (Exception e) {
            log.warn("Failed to parse service completion message: {}", payload, e);
        }
    }
}

