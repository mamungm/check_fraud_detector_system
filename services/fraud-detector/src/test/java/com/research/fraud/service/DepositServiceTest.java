package com.research.fraud.service;

import com.research.fraud.db.entity.DepositEvent;
import com.research.fraud.db.repo.DepositEventRepository;
import com.research.fraud.dto.DepositEventDTO;
import com.research.fraud.dto.DepositEventRequest;
import com.research.fraud.dto.DepositEventResponse;
import com.research.fraud.mappers.DepositEventDTOMapper;
import com.research.fraud.mappers.DepositEventMapper;
import com.research.fraud.statemachine.FraudDetectionWorkflowEntity;
import com.research.fraud.statemachine.FraudDetectionWorkflowRepo;
import com.research.fraud.statemachine.FraudWorkflowService;
import com.research.fraud.statemachine.WorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepositServiceTest {

    @Mock
    FraudWorkflowService fraudWorkflowService;

    @Mock
    FraudDetectionWorkflowRepo fraudDetectionWorkflowRepo;

    @Mock
    DepositEventRepository depositEventRepository;

    @Mock
    DepositEventMapper depositEventMapper;

    @Mock
    DepositEventDTOMapper depositEventDtoMapper;

    DepositService depositService;

    @BeforeEach
    void setUp() {
        depositService = new DepositService(
                fraudWorkflowService,
                fraudDetectionWorkflowRepo,
                depositEventRepository,
                depositEventMapper,
                depositEventDtoMapper
        );
    }

    @Test
    void ingest_persistsDepositStartsWorkflowAndReturnsResponse() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();

        DepositEventRequest request = new DepositEventRequest(
                institutionId,
                UUID.randomUUID(),
                DepositEvent.Channel.mobile,
                OffsetDateTime.now(ZoneOffset.UTC),
                BigDecimal.valueOf(125.75),
                "CAD",
                "acct-123",
                "payee-123",
                "payor-123",
                "dev-123",
                "CA-ON",
                "check-hash",
                "routing-hash",
                "account-hash",
                "front-uri",
                "back-uri"
        );

        DepositEvent mappedEvent = DepositEvent.builder()
                .eventId(eventId)
                .institutionId(institutionId)
                .clearingInstitutionId(request.clearingInstitutionId())
                .channel(request.channel())
                .depositTimestamp(request.depositTimestamp())
                .amount(request.amount())
                .currency(request.currency())
                .accountToken(request.accountToken())
                .payeeToken(request.payeeToken())
                .payorToken(request.payorToken())
                .deviceToken(request.deviceToken())
                .region(request.region())
                .checkSerialHash(request.checkSerialHash())
                .micrRoutingHash(request.micrRoutingHash())
                .micrAccountHash(request.micrAccountHash())
                .imageFrontUri(request.imageFrontUri())
                .imageBackUri(request.imageBackUri())
                .build();

        DepositEvent savedEvent = DepositEvent.builder()
                .eventId(eventId)
                .institutionId(institutionId)
                .clearingInstitutionId(request.clearingInstitutionId())
                .channel(request.channel())
                .depositTimestamp(request.depositTimestamp())
                .amount(request.amount())
                .currency(request.currency())
                .accountToken(request.accountToken())
                .payeeToken(request.payeeToken())
                .payorToken(request.payorToken())
                .deviceToken(request.deviceToken())
                .region(request.region())
                .checkSerialHash(request.checkSerialHash())
                .micrRoutingHash(request.micrRoutingHash())
                .micrAccountHash(request.micrAccountHash())
                .imageFrontUri(request.imageFrontUri())
                .imageBackUri(request.imageBackUri())
                .workflow(WorkflowState.RECEIVED)
                .build();

        DepositEventDTO dto = DepositEventDTO.builder()
                .eventId(eventId)
                .institutionId(institutionId)
                .workflow(WorkflowState.RECEIVED)
                .build();

        DepositEventResponse response = DepositEventResponse.builder()
                .depositEventList(List.of(dto))
                .message("deposit request initiated")
                .build();

        when(depositEventMapper.toEntity(request)).thenReturn(mappedEvent);
        when(depositEventRepository.save(mappedEvent)).thenReturn(savedEvent);
        doNothing().when(fraudWorkflowService).startWorkflow(savedEvent);
        when(depositEventDtoMapper.buildDepositResponse(savedEvent, "deposit request initiated")).thenReturn(response);

        DepositEventResponse actual = depositService.ingest(request);

        assertNotNull(actual);
        assertEquals("deposit request initiated", actual.message());
        assertEquals(1, actual.depositEventList().size());
        assertEquals(eventId, actual.depositEventList().getFirst().getEventId());

        ArgumentCaptor<DepositEvent> savedEventCaptor = ArgumentCaptor.forClass(DepositEvent.class);
        verify(depositEventRepository).save(savedEventCaptor.capture());
        assertEquals(WorkflowState.RECEIVED, savedEventCaptor.getValue().getWorkflow());

        verify(depositEventMapper).toEntity(request);
        verify(fraudWorkflowService).startWorkflow(savedEvent);
        verify(fraudDetectionWorkflowRepo).save(any(FraudDetectionWorkflowEntity.class));
        verify(depositEventDtoMapper).buildDepositResponse(savedEvent, "deposit request initiated");
    }
}
