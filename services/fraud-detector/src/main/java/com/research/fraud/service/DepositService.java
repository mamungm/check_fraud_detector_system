package com.research.fraud.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.research.fraud.db.entity.DepositEvent;
import com.research.fraud.db.repo.DepositEventRepository;
import com.research.fraud.dto.DepositEventRequest;
import com.research.fraud.dto.DepositEventResponse;
import com.research.fraud.kafka.DepositProcessingTracker;
import com.research.fraud.mappers.DepositEventMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class DepositService {
    private final DepositEventRepository depositEventRepository;
    private final KafkaTemplate<String, String> kafka;
    private final DepositEventMapper depositEventMapper;
    private final ObjectMapper objectMapper;
    private final DepositProcessingTracker processingTracker;

    public DepositService(DepositEventRepository depositEventRepository, KafkaTemplate<String, String> kafka,
                          DepositEventMapper depositEventMapper, ObjectMapper objectMapper,
                          DepositProcessingTracker processingTracker) {
        this.depositEventRepository = depositEventRepository;
        this.kafka = kafka;
        this.depositEventMapper = depositEventMapper;
        this.objectMapper = objectMapper;
        this.processingTracker = processingTracker;
    }

    public DepositEventResponse ingest(DepositEventRequest depositEventRequest) throws Exception {
        DepositEvent depositEvent = depositEventMapper.toEntity(depositEventRequest);
        depositEvent = depositEventRepository.save(depositEvent);

        // Track async downstream processing (Kafka fan-out). This is independent of the
        // synchronous HTTP scoring calls below.
        processingTracker.register(depositEvent.getEventId());
        kafka.send("check.deposit.created", depositEvent.getEventId().toString(),
                objectMapper.writeValueAsString(depositEvent));

        return DepositEventResponse.builder()
                .eventId(depositEvent.getEventId())
                .message("Created a deposit request")
                .build();
    }
}
