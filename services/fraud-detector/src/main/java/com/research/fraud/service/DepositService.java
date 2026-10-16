package com.research.fraud.service;

import com.research.fraud.db.entity.DepositEvent;
import com.research.fraud.db.repo.DepositEventRepository;
import com.research.fraud.dto.DepositEventDTO;
import com.research.fraud.dto.DepositEventRequest;
import com.research.fraud.dto.DepositEventResponse;
import com.research.fraud.dto.WorkflowDetailsDTO;
import com.research.fraud.mappers.DepositEventDTOMapper;
import com.research.fraud.mappers.DepositEventMapper;
import com.research.fraud.statemachine.FraudDetectionWorkflowEntity;
import com.research.fraud.statemachine.FraudDetectionWorkflowRepo;
import com.research.fraud.statemachine.FraudWorkflowService;
import com.research.fraud.statemachine.WorkflowState;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DepositService {
    private final FraudWorkflowService fraudWorkflowService;
    private final FraudDetectionWorkflowRepo fraudDetectionWorkflowRepo;
    private final DepositEventRepository depositEventRepository;
    private final DepositEventMapper depositEventMapper;
    private final DepositEventDTOMapper depositEventDtoMapper;

    @Transactional
    public DepositEventResponse ingest(DepositEventRequest depositEventRequest) throws Exception {
        DepositEvent depositEvent = depositEventMapper.toEntity(depositEventRequest);
        depositEvent.setWorkflow(WorkflowState.RECEIVED);
        log.info("workflow state at the start of ingest = {}", WorkflowState.RECEIVED);
        depositEvent = depositEventRepository.save(depositEvent);

        FraudDetectionWorkflowEntity workflowEntity = FraudDetectionWorkflowEntity.builder()
                .eventId(depositEvent.getEventId())
                .createdAt(Instant.now())
                .build();
        fraudDetectionWorkflowRepo.save(workflowEntity);

        fraudWorkflowService.startWorkflow(depositEvent);

        return depositEventDtoMapper.buildDepositResponse(depositEvent, "deposit request initiated");
    }

    public List<DepositEventDTO> getDepositEventList() {
        List<DepositEvent> events = depositEventRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        Map<UUID, FraudDetectionWorkflowEntity> workflows = fraudDetectionWorkflowRepo
                .findAllById(events.stream().map(DepositEvent::getEventId).toList())
                .stream()
                .collect(Collectors.toMap(FraudDetectionWorkflowEntity::getEventId, w -> w));
        return events.stream()
                .map(e -> depositEventDtoMapper.toDTO(e, workflows.get(e.getEventId())))
                .collect(Collectors.toList());
    }

    public Optional<WorkflowDetailsDTO> getWorkflowDetails(UUID eventId) {
        return depositEventDtoMapper.buildWorkflowDetails(eventId);
    }
}
