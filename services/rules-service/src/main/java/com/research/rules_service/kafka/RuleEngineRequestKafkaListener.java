package com.research.rules_service.kafka;

import com.research.rules_service.config.Constants;
import com.research.rules_service.dto.RulesServiceRequest;
import com.research.rules_service.dto.ServiceCompletionEvent;
import com.research.rules_service.service.RulesEngine;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.kafka.annotation.KafkaListener;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Component
@AllArgsConstructor
@Slf4j
public class RuleEngineRequestKafkaListener implements Constants {
    private final ObjectMapper objectMapper;
    private final RulesEngine rulesEngine;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(
            topics = "Rule_Service_CMD",
            groupId = "image-fraud-service"
    )
    public void onRuleServiceCMDMessageReceived(String payload) {
        try {
            log.info("Received Rule_Service_CMD message: {}", payload);
            RulesServiceRequest rulesServiceRequest = objectMapper.readValue(payload, RulesServiceRequest.class);
            List<Map<String, Object>> evaluationResult = rulesEngine.evaluate(rulesServiceRequest);
//            String wsSessionId = eventWSSessionMapper.getWSSessionIdFromEventId(evt.eventId());
//            log.info("WS Session Id: {}", wsSessionId);
            ServiceCompletionEvent serviceCompletionEvent = ServiceCompletionEvent.builder()
                    .eventId(rulesServiceRequest.eventId())
                    .service("rule")
                    .status("SUCCESS")
                    .latencyMs(1)
                    .ts(10.0)
                    .details(evaluationResult)
                    .build();

            Thread.sleep(2000);
            log.info("Rule_Service_CMD completed, serviceCompletionEvent = {}", serviceCompletionEvent);
            String serviceCompletionEventString = objectMapper.writeValueAsString(serviceCompletionEvent);
            kafkaTemplate.send(
                    Rule_Service_Response,
                    serviceCompletionEventString
            );
        } catch (Exception e) {
            log.warn("Failed to parse service completion message: {}", payload, e);
        }
    }
}
