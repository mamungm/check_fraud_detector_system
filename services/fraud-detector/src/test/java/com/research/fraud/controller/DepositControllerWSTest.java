package com.research.fraud.controller;

import com.research.fraud.db.entity.DepositEvent;
import com.research.fraud.dto.DepositEventDTO;
import com.research.fraud.dto.DepositEventRequest;
import com.research.fraud.dto.DepositEventResponse;
import com.research.fraud.service.DepositService;
import com.research.fraud.service.EventWSSessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DepositControllerWSTest {

    @Mock
    DepositService depositService;

    @Mock
    EventWSSessionMapper eventWSSessionMapper;

    DepositControllerWS controllerWS;

    @BeforeEach
    void setUp() {
        controllerWS = new DepositControllerWS(depositService, eventWSSessionMapper);
    }

    @Test
    void depositRequest_happyPath_ingestsAndMapsSessionId() throws Exception {
        UUID eventId = UUID.randomUUID();
        String sessionId = "ws-session-1";

        DepositEventRequest request = new DepositEventRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                DepositEvent.Channel.mobile,
                OffsetDateTime.now(),
                new BigDecimal("100.00"),
                "CAD",
                "account-1",
                "payee-1",
                "payor-1",
                "device-1",
                "CA",
                "check-serial",
                "micr-routing",
                "micr-account",
                "front-uri",
                "back-uri"
        );

        DepositEventDTO dto = DepositEventDTO.builder()
                .eventId(eventId)
                .amount(new BigDecimal("100.00"))
                .currency("CAD")
                .channel(DepositEvent.Channel.mobile)
                .build();

        DepositEventResponse response = DepositEventResponse.builder()
                .depositEventList(new ArrayList<>(List.of(dto)))
                .message("deposit request initiated")
                .build();

        when(depositService.ingest(any(DepositEventRequest.class))).thenReturn(response);

        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create();
        headerAccessor.setSessionId(sessionId);

        DepositEventResponse actual = controllerWS.depositRequest(request, headerAccessor);

        assertSame(response, actual);
        verify(depositService, times(1)).ingest(eq(request));
        verify(eventWSSessionMapper, times(1)).mapEventToWSSession(eq(eventId), eq(sessionId));
    }

    @Test
    void depositRequest_whenDepositEventListEmpty_throwsAndDoesNotMap() throws Exception {
        String sessionId = "ws-session-2";

        DepositEventRequest request = new DepositEventRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                DepositEvent.Channel.ATM,
                OffsetDateTime.now(),
                new BigDecimal("50.00"),
                "CAD",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        DepositEventResponse response = DepositEventResponse.builder()
                .depositEventList(new ArrayList<>())
                .message("deposit request initiated")
                .build();

        when(depositService.ingest(any(DepositEventRequest.class))).thenReturn(response);

        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create();
        headerAccessor.setSessionId(sessionId);

        assertThrows(Exception.class, () -> controllerWS.depositRequest(request, headerAccessor));

        verify(depositService, times(1)).ingest(eq(request));
        verifyNoInteractions(eventWSSessionMapper);
    }
}

