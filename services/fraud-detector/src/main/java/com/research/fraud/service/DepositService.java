package com.research.fraud.service;

import com.research.fraud.db.entity.DepositEvent;
import com.research.fraud.db.repo.DepositEventRepository;
import com.research.fraud.dto.DepositEventDTO;
import com.research.fraud.dto.DepositEventRequest;
import com.research.fraud.dto.DepositEventResponse;
import com.research.fraud.mappers.DepositEventMapper;
import com.research.fraud.statemachine.FraudDetectionWorkflowEntity;
import com.research.fraud.statemachine.FraudDetectionWorkflowRepo;
import com.research.fraud.statemachine.FraudWorkflowService;
import com.research.fraud.statemachine.WorkflowState;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class DepositService {
    private final FraudWorkflowService fraudWorkflowService;
    private final FraudDetectionWorkflowRepo fraudDetectionWorkflowRepo;
    private final DepositEventRepository depositEventRepository;
    private final DepositEventMapper depositEventMapper;

    @Transactional
    public DepositEventResponse ingest(DepositEventRequest depositEventRequest) throws Exception {
        UUID eventId = UUID.randomUUID();
        FraudDetectionWorkflowEntity workflowEntity = FraudDetectionWorkflowEntity.builder()
                .eventId(eventId)
                .state(WorkflowState.RECEIVED)
                .createdAt(Instant.now())
                .build();
        workflowEntity = fraudDetectionWorkflowRepo.save(workflowEntity);

        DepositEvent depositEvent = depositEventMapper.toEntity(depositEventRequest);
        depositEvent.setWorkflow(workflowEntity);
        depositEvent = depositEventRepository.save(depositEvent);

        fraudWorkflowService.startWorkflow(depositEvent);

        List<DepositEventDTO> depositEventDTOS = Stream.of(depositEvent).map(this::toDTO).toList();

        return DepositEventResponse.builder()
                .depositEventList(depositEventDTOS)
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
                .workflow(e.getWorkflow().getState())
                .finalFraudProbability(0)
                .createdAt(e.getCreatedAt())
                .build();
    }
}
