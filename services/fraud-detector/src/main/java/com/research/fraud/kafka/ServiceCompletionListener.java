package com.research.fraud.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ServiceCompletionListener {
    private static final Logger log = LoggerFactory.getLogger(ServiceCompletionListener.class);

    private final ObjectMapper objectMapper;
    private final DepositProcessingTracker tracker;

    public ServiceCompletionListener(ObjectMapper objectMapper, DepositProcessingTracker tracker) {
        this.objectMapper = objectMapper;
        this.tracker = tracker;
    }

    @KafkaListener(
            topics = "${fraud.kafka.topics.service-completed:check.deposit.service.completed}",
            groupId = "${fraud.kafka.consumer-group:fraud-detector-completions}"
    )
    public void onMessage(String payload) {
//        try {
//            ServiceCompletionEvent evt = objectMapper.readValue(payload, ServiceCompletionEvent.class);
//            tracker.onServiceCompletion(evt);
//        } catch (Exception e) {
//            log.warn("Failed to parse service completion message: {}", payload, e);
//        }
    }
}

