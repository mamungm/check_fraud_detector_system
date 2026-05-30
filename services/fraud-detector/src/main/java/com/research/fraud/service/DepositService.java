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
import com.research.fraud.utils.DTOConverter;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DepositService {
    private final FraudWorkflowService fraudWorkflowService;
    private final FraudDetectionWorkflowRepo fraudDetectionWorkflowRepo;
    private final DepositEventRepository depositEventRepository;
    private final DepositEventMapper depositEventMapper;
    private final DTOConverter dtoConverter;

    @Transactional
    public DepositEventResponse ingest(DepositEventRequest depositEventRequest) throws Exception {
        DepositEvent depositEvent = depositEventMapper.toEntity(depositEventRequest);
        depositEvent.setWorkflow(WorkflowState.RECEIVED);

        depositEvent.setWorkflow(WorkflowState.WAITING_FOR_DEPENDENCIES);
        depositEvent.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        depositEvent = depositEventRepository.save(depositEvent);

        fraudWorkflowService.startWorkflow(depositEvent);

        FraudDetectionWorkflowEntity workflowEntity = FraudDetectionWorkflowEntity.builder()
                .eventId(depositEvent.getEventId())
                .createdAt(Instant.now())
                .build();
        fraudDetectionWorkflowRepo.save(workflowEntity);

        return dtoConverter.buildDepositResponse(depositEvent, "deposit request initiated");
    }

    public List<DepositEventDTO> getDepositEventList() {
        return depositEventRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(dtoConverter::toDTO)
                .collect(Collectors.toList());
    }
}
