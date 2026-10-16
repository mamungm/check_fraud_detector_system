package com.research.fraud.mappers;

import com.research.fraud.db.entity.DepositEvent;
import com.research.fraud.dto.DepositEventDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.research.fraud.dto.DepositEventResponse;
import com.research.fraud.dto.WorkflowDetailsDTO;
import com.research.fraud.statemachine.FraudDetectionWorkflowEntity;
import com.research.fraud.statemachine.FraudDetectionWorkflowRepo;
import com.research.fraud.statemachine.WorkflowState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DepositEventDTOMapperTest {

    private final FraudDetectionWorkflowRepo workflowRepo = mock(FraudDetectionWorkflowRepo.class);
    private final DepositEventDTOMapper mapper = new DepositEventDTOMapper(workflowRepo, new ObjectMapper());

    @Test
    void toDTO_mapsAllFields_andSetsFinalFraudProbabilityToZero() {
        UUID eventId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        UUID clearingInstitutionId = UUID.randomUUID();
        OffsetDateTime ts = OffsetDateTime.of(2026, 6, 5, 10, 11, 12, 0, ZoneOffset.UTC);
        OffsetDateTime createdAt = OffsetDateTime.of(2026, 6, 5, 10, 11, 13, 0, ZoneOffset.UTC);

        DepositEvent event = DepositEvent.builder()
                .eventId(eventId)
                .institutionId(institutionId)
                .clearingInstitutionId(clearingInstitutionId)
                .channel(DepositEvent.Channel.mobile)
                .depositTimestamp(ts)
                .amount(new BigDecimal("123.45"))
                .currency("CAD")
                .accountToken("acc")
                .payeeToken("payee")
                .payorToken("payor")
                .deviceToken("device")
                .region("CA")
                .checkSerialHash("check")
                .micrRoutingHash("routing")
                .micrAccountHash("account")
                .imageFrontUri("front")
                .imageBackUri("back")
                .fraudLabel(false)
                .workflow(WorkflowState.RECEIVED)
                .createdAt(createdAt)
                .build();

        DepositEventDTO dto = mapper.toDTO(event);

        assertEquals(eventId, dto.getEventId());
        assertEquals(institutionId, dto.getInstitutionId());
        assertEquals(clearingInstitutionId, dto.getClearingInstitutionId());
        assertEquals(DepositEvent.Channel.mobile, dto.getChannel());
        assertEquals(ts, dto.getDepositTimestamp());
        assertEquals(new BigDecimal("123.45"), dto.getAmount());
        assertEquals("CAD", dto.getCurrency());
        assertEquals("acc", dto.getAccountToken());
        assertEquals("payee", dto.getPayeeToken());
        assertEquals("payor", dto.getPayorToken());
        assertEquals("device", dto.getDeviceToken());
        assertEquals("CA", dto.getRegion());
        assertEquals("check", dto.getCheckSerialHash());
        assertEquals("routing", dto.getMicrRoutingHash());
        assertEquals("account", dto.getMicrAccountHash());
        assertEquals("front", dto.getImageFrontUri());
        assertEquals("back", dto.getImageBackUri());
        assertEquals(WorkflowState.RECEIVED, dto.getWorkflow());
        assertEquals(0.0f, dto.getFinalFraudProbability());
        assertEquals(createdAt, dto.getCreatedAt());
    }

    @Test
    void buildDepositResponse_wrapsSingleDto_andUsesMessage() {
        DepositEvent event = DepositEvent.builder()
                .eventId(UUID.randomUUID())
                .depositTimestamp(OffsetDateTime.now())
                .amount(new BigDecimal("10.00"))
                .currency("CAD")
                .workflow(WorkflowState.RECEIVED)
                .build();

        DepositEventResponse response = mapper.buildDepositResponse(event, "hello");

        assertNotNull(response);
        assertEquals("hello", response.message());
        assertNotNull(response.depositEventList());
        assertEquals(1, response.depositEventList().size());
        assertEquals(event.getEventId(), response.depositEventList().getFirst().getEventId());
    }

    private DepositEvent simpleEvent() {
        return DepositEvent.builder()
                .eventId(UUID.randomUUID())
                .depositTimestamp(OffsetDateTime.now())
                .amount(new BigDecimal("10.00"))
                .currency("CAD")
                .workflow(WorkflowState.COMPLETED)
                .build();
    }

    @Test
    void toDTO_readsScoresAndServiceFlagsFromWorkflow() {
        DepositEvent event = simpleEvent();
        FraudDetectionWorkflowEntity workflow = FraudDetectionWorkflowEntity.builder()
                .eventId(event.getEventId())
                .imageCompleted(true)
                .consortiumCompleted(true)
                .mlCompleted(true)
                .ruleCompleted(false)
                .mlResult("{\"status\":\"SUCCESS\",\"details\":{\"depositFraudProbability\":0.42,"
                        + "\"finalFraudProbability\":0.91,\"calibratedRiskBand\":\"HIGH\","
                        + "\"recommendedAction\":\"HOLD\"}}")
                .build();

        DepositEventDTO dto = mapper.toDTO(event, workflow);

        assertEquals(0.42f, dto.getDepositFraudProbability(), 0.0001f);
        assertEquals(0.91f, dto.getFinalFraudProbability(), 0.0001f);
        assertEquals("HIGH", dto.getCalibratedRiskBand());
        assertEquals("HOLD", dto.getRecommendedAction());
        assertTrue(dto.isImageCompleted());
        assertTrue(dto.isConsortiumCompleted());
        assertTrue(dto.isMlCompleted());
        assertFalse(dto.isRuleCompleted());
    }

    @Test
    void toDTO_ignoresFailedOrMissingMlResult() {
        DepositEvent event = simpleEvent();
        FraudDetectionWorkflowEntity failed = FraudDetectionWorkflowEntity.builder()
                .eventId(event.getEventId())
                .mlCompleted(true)
                .mlResult("{\"status\":\"FAILED\",\"error\":\"boom\"}")
                .build();

        DepositEventDTO dto = mapper.toDTO(event, failed);
        assertEquals(0.0f, dto.getFinalFraudProbability());
        assertNull(dto.getCalibratedRiskBand());
        assertNull(dto.getRecommendedAction());
        assertTrue(dto.isMlCompleted());

        DepositEventDTO noWorkflow = mapper.toDTO(event, null);
        assertEquals(0.0f, noWorkflow.getFinalFraudProbability());
        assertFalse(noWorkflow.isImageCompleted());
        assertFalse(noWorkflow.isMlCompleted());
    }

    @Test
    void toDTO_doesNotThrowOnInvalidMlJson() {
        DepositEvent event = simpleEvent();
        FraudDetectionWorkflowEntity workflow = FraudDetectionWorkflowEntity.builder()
                .eventId(event.getEventId())
                .mlResult("not json")
                .build();

        assertDoesNotThrow(() -> mapper.toDTO(event, workflow));
    }

    @Test
    void buildWorkflowDetails_parsesEachServiceResult() {
        UUID eventId = UUID.randomUUID();
        FraudDetectionWorkflowEntity workflow = FraudDetectionWorkflowEntity.builder()
                .eventId(eventId)
                .consortiumCompleted(true)
                .imageCompleted(true)
                .ruleCompleted(true)
                .mlCompleted(false)
                .consortiumResult("{\"service\":\"consortium\",\"details\":{\"score\":0.5}}")
                .imageResult("not json")
                .ruleResult("{\"service\":\"rule\",\"details\":{}}")
                .build();
        when(workflowRepo.findById(eventId)).thenReturn(Optional.of(workflow));

        WorkflowDetailsDTO dto = mapper.buildWorkflowDetails(eventId).orElseThrow();

        Map<?, ?> consortiumDetails = (Map<?, ?>) dto.getConsortiumResult().get("details");
        assertEquals(0.5, ((Number) consortiumDetails.get("score")).doubleValue(), 0.0001);
        assertEquals("consortium", dto.getConsortiumResult().get("service"));
        assertNull(dto.getImageResult());          // unreadable result is dropped, not an error
        assertTrue(dto.getRuleResult().get("details") instanceof Map);
        assertNull(dto.getMlResult());             // ML has not responded yet
        assertTrue(dto.isConsortiumCompleted());
        assertFalse(dto.isMlCompleted());
    }

    @Test
    void buildWorkflowDetails_emptyWhenWorkflowMissing() {
        assertTrue(mapper.buildWorkflowDetails(UUID.randomUUID()).isEmpty());
    }
}
