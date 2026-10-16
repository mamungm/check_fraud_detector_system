package com.research.rules_service.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.research.rules_service.config.Constants;
import com.research.rules_service.dto.RulesServiceRequest;
import com.research.rules_service.dto.ServiceCompletionEvent;
import com.research.rules_service.service.RulesEngine;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@AllArgsConstructor
@Slf4j
public class RuleEngineRequestKafkaListener implements Constants {
    private final ObjectMapper objectMapper;
    private final RulesEngine rulesEngine;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(
            topics = "Rule_Service_CMD",
            groupId = "rule-service"
    )
    public void onRuleServiceCMDMessageReceived(String payload) {
        long start = System.currentTimeMillis();
        UUID eventId = null;
        try {
            log.info("Received Rule_Service_CMD message: {}", payload);
            RulesServiceRequest request = objectMapper.readValue(payload, RulesServiceRequest.class);
            eventId = request.eventId();
            Map<String, Object> hits = rulesEngine.evaluate(request);
            send(completion(eventId, "SUCCESS", start, null, hits));
        } catch (Exception e) {
            log.error("Rule_Service_CMD failed, payload: {}", payload, e);
            // Without a response fraud-detector would wait forever, so report the failure.
            if (eventId != null) {
                try {
                    send(completion(eventId, "FAILED", start, String.valueOf(e.getMessage()), null));
                } catch (Exception sendError) {
                    log.error("Could not send failure completion for event {}", eventId, sendError);
                }
            }
        }
    }

    private ServiceCompletionEvent completion(UUID eventId, String status, long start, String error,
                                              Map<String, Object> details) {
        long now = System.currentTimeMillis();
        return ServiceCompletionEvent.builder()
                .eventId(eventId)
                .service("rule")
                .status(status)
                .latencyMs((int) (now - start))
                .ts(now / 1000.0)
                .error(error)
                .details(details)
                .build();
    }

    private void send(ServiceCompletionEvent event) throws Exception {
        log.info("Rule_Service_CMD completed, serviceCompletionEvent = {}", event);
        kafkaTemplate.send(Rule_Service_Response, objectMapper.writeValueAsString(event));
    }
}
