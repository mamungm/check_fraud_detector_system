package com.research.fraud.mappers;

import com.research.fraud.db.entity.DepositEvent;
import com.research.fraud.dto.DepositEventDTO;
import com.research.fraud.dto.DepositEventResponse;
import com.research.fraud.statemachine.WorkflowState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DepositEventDTOMapperTest {

    private final DepositEventDTOMapper mapper = new DepositEventDTOMapper();

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
}

