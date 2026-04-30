package com.research.fraud.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.research.fraud.db.repo.DepositEventRepository;
import com.research.fraud.dto.ServiceCompletionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DepositProcessingTracker {
    private static final Logger log = LoggerFactory.getLogger(DepositProcessingTracker.class);

    private final Set<String> requiredServices;
    private final Duration ttl;
    private final String allServicesDoneTopic;

    private final ConcurrentHashMap<UUID, ProcessingState> states = new ConcurrentHashMap<>();

    private final DepositEventRepository depositEventRepository;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;

    public DepositProcessingTracker(
            DepositEventRepository depositEventRepository,
            KafkaTemplate<String, String> kafka,
            ObjectMapper objectMapper,
            @Value("${fraud.kafka.required-services:consortium,image,ml}") String requiredServices,
            @Value("${fraud.kafka.tracker-ttl:PT10M}") Duration ttl,
            @Value("${fraud.kafka.topics.all-services-completed:check.deposit.all.services.completed}") String allServicesDoneTopic
    ) {
        this.depositEventRepository = depositEventRepository;
        this.kafka = kafka;
        this.objectMapper = objectMapper;
        this.requiredServices = Set.copyOf(Arrays.asList(requiredServices.split("\\s*,\\s*")));
        this.ttl = ttl;
        this.allServicesDoneTopic = allServicesDoneTopic;

        log.info("DepositProcessingTracker requiredServices={} ttl={} allDoneTopic={}",
                this.requiredServices, this.ttl, this.allServicesDoneTopic);
    }

    /**
     * Called when fraud-detector publishes a deposit-created event.
     */
    public void register(UUID eventId) {
        states.putIfAbsent(eventId, new ProcessingState(requiredServices));
        cleanupExpired();
    }

    /**
     * Called by the Kafka listener when any downstream service finishes.
     */
    public void onServiceCompletion(ServiceCompletionEvent evt) {
        if (evt == null || evt.eventId() == null || evt.service() == null) {
            return;
        }

        ProcessingState state = states.computeIfAbsent(evt.eventId(), _id -> new ProcessingState(requiredServices));
        state.mark(evt.service(), evt.status());

        if (state.isComplete()) {
            boolean allOk = state.allSuccessful();
            log.info("All services completed for eventId={} allOk={} statuses={}", evt.eventId(), allOk, state.statusByService);

            // Persist a coarse lifecycle status on the deposit event (MVP)
            depositEventRepository.findById(evt.eventId()).ifPresent(depositEvent -> {
                depositEvent.setStatus(allOk ? "ASYNC_ENRICHED" : "ASYNC_FAILED");
                depositEventRepository.save(depositEvent);
            });

            // Emit a single "all done" event (optional, but nice for chaining)
            try {
                String payload = objectMapper.writeValueAsString(Map.of(
                        "eventId", evt.eventId(),
                        "status", allOk ? "SUCCESS" : "FAILED",
                        "services", state.statusByService,
                        "completedAt", Instant.now().toString()
                ));
                kafka.send(allServicesDoneTopic, evt.eventId().toString(), payload);
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize all-services-completed event", e);
            }

            // Remove from memory to prevent growth
            states.remove(evt.eventId());
        }

        cleanupExpired();
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        states.entrySet().removeIf(e -> Duration.between(e.getValue().createdAt, now).compareTo(ttl) > 0);
    }

    private static final class ProcessingState {
        private final Instant createdAt = Instant.now();
        private final Set<String> required;
        private final ConcurrentHashMap<String, String> statusByService = new ConcurrentHashMap<>();

        private ProcessingState(Set<String> required) {
            this.required = required;
        }

        private void mark(String service, String status) {
            if (service == null) return;
            statusByService.put(service, status == null ? "UNKNOWN" : status);
        }

        private boolean isComplete() {
            return statusByService.keySet().containsAll(required);
        }

        private boolean allSuccessful() {
            return required.stream().allMatch(s -> "SUCCESS".equalsIgnoreCase(statusByService.get(s)));
        }
    }
}

