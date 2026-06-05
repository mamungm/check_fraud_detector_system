package com.research.fraud.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.research.fraud.db.entity.DepositEvent;
import com.research.fraud.dto.DepositEventDTO;
import com.research.fraud.dto.DepositEventRequest;
import com.research.fraud.dto.DepositEventResponse;
import com.research.fraud.service.DepositService;
import com.research.fraud.statemachine.WorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class DepositControllerTest {

    MockMvc mockMvc;

    ObjectMapper objectMapper;

    @Mock
    DepositService depositService;

    @BeforeEach
    void setUp() {
        DepositController controller = new DepositController(depositService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new TestExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
    }

    /**
     * In the full Spring Boot stack, unhandled exceptions typically become HTTP 500.
     * With standalone MockMvc, we install a minimal exception handler to get the same behavior.
     */
    @RestControllerAdvice
    static class TestExceptionHandler {
        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
            return ResponseEntity.badRequest().build();
        }

        @ExceptionHandler(RuntimeException.class)
        public ResponseEntity<Void> handleRuntimeException(RuntimeException ex) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Test: POST /api/deposit_request with valid request
     * Expects: 200 OK with DepositEventResponse
     */
    @Test
    void ingest_validRequest_returns200WithResponse() throws Exception {
        UUID institutionId = UUID.randomUUID();
        UUID clearingInstitutionId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        DepositEventRequest request = new DepositEventRequest(
                institutionId,
                clearingInstitutionId,
                DepositEvent.Channel.mobile,
                now,
                new BigDecimal("100.00"),
                "CAD",
                "acc-token-1",
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

        DepositEventResponse response = DepositEventResponse.builder()
                .depositEventList(new ArrayList<>())
                .message("deposit request initiated")
                .build();

        when(depositService.ingest(any(DepositEventRequest.class)))
                .thenReturn(response);

        // Act and Assert
        mockMvc.perform(post("/api/deposit_request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", equalTo("deposit request initiated")))
                .andExpect(jsonPath("$.depositEventList", hasSize(0)));

        verify(depositService, times(1)).ingest(any(DepositEventRequest.class));
    }

    /**
     * Test: POST /api/deposit_request with minimal valid request
     * Expects: 200 OK with DepositEventResponse
     */
    @Test
    void ingest_minimalValidRequest_returns200() throws Exception {
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

        when(depositService.ingest(any(DepositEventRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/deposit_request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", equalTo("deposit request initiated")));

        verify(depositService, times(1)).ingest(any(DepositEventRequest.class));
    }

    /**
     * Test: POST /api/deposit_request where service throws exception
     * Expects: Propagates exception (500 Internal Server Error)
     */
    @Test
    void ingest_serviceThrowsException_returns500() throws Exception {
        DepositEventRequest request = new DepositEventRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                DepositEvent.Channel.branch,
                OffsetDateTime.now(),
                new BigDecimal("25.00"),
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

        when(depositService.ingest(any(DepositEventRequest.class)))
                .thenThrow(new RuntimeException("Service error"));

        mockMvc.perform(post("/api/deposit_request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());

        verify(depositService, times(1)).ingest(any(DepositEventRequest.class));
    }

    /**
     * Test: GET /api/deposit_event_list with deposits
     * Expects: 200 OK with list of DepositEventDTO
     */
    @Test
    void getDepositEventList_withDeposits_returns200WithList() throws Exception {
        UUID eventId1 = UUID.randomUUID();
        UUID eventId2 = UUID.randomUUID();

        DepositEventDTO dto1 = DepositEventDTO.builder()
                .eventId(eventId1)
                .amount(new BigDecimal("100.00"))
                .currency("CAD")
                .channel(DepositEvent.Channel.mobile)
                .workflow(WorkflowState.COMPLETED)
                .finalFraudProbability(0.2f)
                .build();

        DepositEventDTO dto2 = DepositEventDTO.builder()
                .eventId(eventId2)
                .amount(new BigDecimal("50.00"))
                .currency("CAD")
                .channel(DepositEvent.Channel.ATM)
                .workflow(WorkflowState.RECEIVED)
                .finalFraudProbability(0.5f)
                .build();

        List<DepositEventDTO> deposits = List.of(dto1, dto2);

        when(depositService.getDepositEventList())
                .thenReturn(deposits);

        mockMvc.perform(get("/api/deposit_event_list")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].eventId", equalTo(eventId1.toString())))
                .andExpect(jsonPath("$[0].amount", equalTo(100.00)))
                .andExpect(jsonPath("$[1].eventId", equalTo(eventId2.toString())))
                .andExpect(jsonPath("$[1].amount", equalTo(50.00)));

        verify(depositService, times(1)).getDepositEventList();
    }

    /**
     * Test: GET /api/deposit_event_list with no deposits
     * Expects: 200 OK with empty list
     */
    @Test
    void getDepositEventList_emptyList_returns200WithEmptyArray() throws Exception {
        when(depositService.getDepositEventList())
                .thenReturn(new ArrayList<>());

        mockMvc.perform(get("/api/deposit_event_list")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        verify(depositService, times(1)).getDepositEventList();
    }

    /**
     * Test: GET /api/deposit_event_list where service throws exception
     * Expects: 500 Internal Server Error
     */
    @Test
    void getDepositEventList_serviceThrowsException_returns500() throws Exception {
        when(depositService.getDepositEventList())
                .thenThrow(new RuntimeException("Database error"));

        mockMvc.perform(get("/api/deposit_event_list")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError());

        verify(depositService, times(1)).getDepositEventList();
    }

    /**
     * Test: POST /api/deposit_request with missing required fields
     * Expects: 400 Bad Request or validation error
     */
    @Test
    void ingest_invalidJson_returnsBadRequest() throws Exception {
        // Unknown enum value for channel -> Jackson fails to deserialize -> 400 Bad Request
        String invalidJson = """
                {
                    "institutionId": "550e8400-e29b-41d4-a716-446655440000",
                    "clearingInstitutionId": "550e8400-e29b-41d4-a716-446655440001",
                    "channel": "NOT_A_REAL_CHANNEL",
                    "depositTimestamp": "2024-01-01T00:00:00Z",
                    "amount": 100.00,
                    "currency": "CAD"
                }
                """;

        mockMvc.perform(post("/api/deposit_request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(depositService);
    }
}
