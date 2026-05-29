package com.research.fraud.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.research.fraud.db.entity.DepositEvent;
import com.research.fraud.db.repo.DepositEventRepository;
import com.research.fraud.dto.DepositEventDTO;
import com.research.fraud.dto.DepositEventRequest;
import com.research.fraud.dto.DepositEventResponse;
import com.research.fraud.kafka.DepositProcessingTracker;
import com.research.fraud.mappers.DepositEventMapper;
import com.research.fraud.statemachine.FraudWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DepositService {
    private final FraudWorkflowService fraudWorkflowService;
    private final DepositEventRepository depositEventRepository;
    private final DepositEventMapper depositEventMapper;
    private final ObjectMapper objectMapper;
    private final DepositProcessingTracker processingTracker;

    public DepositEventResponse ingest(DepositEventRequest depositEventRequest) throws Exception {
        DepositEvent depositEvent = depositEventMapper.toEntity(depositEventRequest);
        depositEvent.setStatus(DepositEvent.Status.RECEIVED);
        depositEvent = depositEventRepository.save(depositEvent);

        // Track async downstream processing (Kafka fan-out). This is independent of the
        // synchronous HTTP scoring calls below.
        processingTracker.register(depositEvent.getEventId());
        fraudWorkflowService.startWorkflow(depositEvent);

        return DepositEventResponse.builder()
                .eventId(depositEvent.getEventId())
                .message("deposit request initiated")
                .build();
    }

    public List<DepositEventDTO> getDepositEventList() {
        return depositEventRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    private DepositEventDTO toDTO(DepositEvent e) {
        return DepositEventDTO.builder()
                .eventId(e.getEventId())
                .institutionId(e.getInstitutionId())
                .clearingInstitutionId(e.getClearingInstitutionId())
                .channel(e.getChannel())
                .depositTimestamp(e.getDepositTimestamp())
                .amount(e.getAmount())
                .currency(e.getCurrency())
                .accountToken(e.getAccountToken())
                .payeeToken(e.getPayeeToken())
                .payorToken(e.getPayorToken())
                .deviceToken(e.getDeviceToken())
                .region(e.getRegion())
                .checkSerialHash(e.getCheckSerialHash())
                .micrRoutingHash(e.getMicrRoutingHash())
                .micrAccountHash(e.getMicrAccountHash())
                .imageFrontUri(e.getImageFrontUri())
                .imageBackUri(e.getImageBackUri())
                .status(e.getStatus())
                .finalFraudProbability(0)
                .createdAt(e.getCreatedAt())
                .build();
    }
}
