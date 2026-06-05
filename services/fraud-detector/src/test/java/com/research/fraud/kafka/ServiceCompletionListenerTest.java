package com.research.fraud.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.research.fraud.dto.*;
import com.research.fraud.service.EventWSSessionMapper;
import com.research.fraud.service.SingleServiceCompletionResponsePreparer;
import com.research.fraud.statemachine.FraudWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.research.fraud.config.Constants.SINGLE_SERVICE_COMPLETION_TOPIC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceCompletionListenerTest {
    ObjectMapper objectMapper;

    @Mock
    FraudWorkflowService fraudWorkflowService;

    @Mock
    EventWSSessionMapper eventWSSessionMapper;

    @Mock
    SimpMessagingTemplate messagingTemplate;

    @Mock
    SingleServiceCompletionResponsePreparer singleServiceCompletionResponsePreparer;

    ServiceCompletionListener listener;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        listener = new ServiceCompletionListener(
                objectMapper,
                fraudWorkflowService,
                eventWSSessionMapper,
                messagingTemplate,
                singleServiceCompletionResponsePreparer
        );
    }

    /**
     * Test Rule service response happy path
     * (Note: Rule handler does NOT call Thread.sleep, unlike other handlers)
     */
    @Test
    void onRuleServiceResponseMessage_validPayload_callsWorkflowAndSendsWsMessage() throws JsonProcessingException {
        UUID eventId = UUID.randomUUID();

        Map<String, Object> payloadMap = new HashMap<>();
        payloadMap.put("eventId", eventId.toString());
        payloadMap.put("service", "rule");
        payloadMap.put("status", "OK");

        String payload = objectMapper.writeValueAsString(payloadMap);

        DepositEventResponse response = DepositEventResponse.builder()
                .depositEventList(Collections.emptyList())
                .message("rule done")
                .build();

        when(singleServiceCompletionResponsePreparer.prepareRuleServiceResponseMessage(any(ServiceCompletionEvent.class)))
                .thenReturn(response);
        when(eventWSSessionMapper.getWSSessionIdFromEventId(eq(eventId))).thenReturn("session-1");

        // Act
        listener.onRuleServiceResponseMessage(payload);

        // Assert: verify fraud workflow called with RuleServiceResponse containing eventId and raw payload
        ArgumentCaptor<RuleServiceResponse> captor = ArgumentCaptor.forClass(RuleServiceResponse.class);
        verify(fraudWorkflowService, times(1)).handleRuleServiceResponse(captor.capture());
        RuleServiceResponse called = captor.getValue();
        assertEquals(eventId, called.getEventId());
        assertEquals(payload, called.getResult());

        // verify preparer and messaging called
        verify(singleServiceCompletionResponsePreparer, times(1)).prepareRuleServiceResponseMessage(any(ServiceCompletionEvent.class));
        verify(messagingTemplate, times(1)).convertAndSend(eq(SINGLE_SERVICE_COMPLETION_TOPIC), eq(response));
    }

    @Test
    void onConsortiumServiceResponseMessage_validPayload_callsWorkflowAndSendsWsMessage() throws JsonProcessingException {
        UUID eventId = UUID.randomUUID();

        Map<String, Object> payloadMap = new HashMap<>();
        payloadMap.put("eventId", eventId.toString());
        payloadMap.put("service", "consortium");
        payloadMap.put("status", "OK");

        String payload = objectMapper.writeValueAsString(payloadMap);

        DepositEventResponse response = DepositEventResponse.builder()
                .depositEventList(Collections.emptyList())
                .message("consortium done")
                .build();

        when(singleServiceCompletionResponsePreparer.prepareConsortiumServiceResponseMessage(any(ServiceCompletionEvent.class)))
                .thenReturn(response);
        when(eventWSSessionMapper.getWSSessionIdFromEventId(eq(eventId))).thenReturn("session-1");

        // Act
        listener.onConsortiumServiceResponseMessage(payload);

        // Assert: verify fraud workflow called
        ArgumentCaptor<ConsortiumServiceResponse> captor = ArgumentCaptor.forClass(ConsortiumServiceResponse.class);
        verify(fraudWorkflowService, times(1)).handleConsortiumServiceResponse(captor.capture());
        ConsortiumServiceResponse called = captor.getValue();
        assertEquals(eventId, called.getEventId());
        assertEquals(payload, called.getResult());

        // verify preparer and messaging called
        verify(singleServiceCompletionResponsePreparer, times(1)).prepareConsortiumServiceResponseMessage(any(ServiceCompletionEvent.class));
        verify(messagingTemplate, times(1)).convertAndSend(eq(SINGLE_SERVICE_COMPLETION_TOPIC), eq(response));
    }

    @Test
    void onImageServiceResponseMessage_validPayload_callsWorkflowAndSendsWsMessage() throws JsonProcessingException {
        UUID eventId = UUID.randomUUID();

        Map<String, Object> payloadMap = new HashMap<>();
        payloadMap.put("eventId", eventId.toString());
        payloadMap.put("service", "image");
        payloadMap.put("status", "OK");

        String payload = objectMapper.writeValueAsString(payloadMap);

        DepositEventResponse response = DepositEventResponse.builder()
                .depositEventList(Collections.emptyList())
                .message("image done")
                .build();

        when(singleServiceCompletionResponsePreparer.prepareImageServiceResponseMessage(any(ServiceCompletionEvent.class)))
                .thenReturn(response);
        when(eventWSSessionMapper.getWSSessionIdFromEventId(eq(eventId))).thenReturn("session-1");

        // Act
        listener.onImageServiceResponseMessage(payload);

        // Assert: verify fraud workflow called
        ArgumentCaptor<ImageServiceResponse> captor = ArgumentCaptor.forClass(ImageServiceResponse.class);
        verify(fraudWorkflowService, times(1)).handleImageServiceResponse(captor.capture());
        ImageServiceResponse called = captor.getValue();
        assertEquals(eventId, called.getEventId());
        assertEquals(payload, called.getResult());

        // verify preparer and messaging called
        verify(singleServiceCompletionResponsePreparer, times(1)).prepareImageServiceResponseMessage(any(ServiceCompletionEvent.class));
        verify(messagingTemplate, times(1)).convertAndSend(eq(SINGLE_SERVICE_COMPLETION_TOPIC), eq(response));
    }

    @Test
    void onMLServiceResponseMessage_validPayload_callsWorkflowAndSendsWsMessage() throws JsonProcessingException {
        UUID eventId = UUID.randomUUID();

        Map<String, Object> payloadMap = new HashMap<>();
        payloadMap.put("eventId", eventId.toString());
        payloadMap.put("service", "ml");
        payloadMap.put("status", "OK");

        String payload = objectMapper.writeValueAsString(payloadMap);

        DepositEventResponse response = DepositEventResponse.builder()
                .depositEventList(Collections.emptyList())
                .message("ml done")
                .build();

        when(singleServiceCompletionResponsePreparer.prepareMLServiceResponseMessage(any(ServiceCompletionEvent.class)))
                .thenReturn(response);
        when(eventWSSessionMapper.getWSSessionIdFromEventId(eq(eventId))).thenReturn("session-1");

        // Act
        listener.onMLServiceResponseMessage(payload);

        // Assert: verify fraud workflow called
        ArgumentCaptor<MLServiceResponse> captor = ArgumentCaptor.forClass(MLServiceResponse.class);
        verify(fraudWorkflowService, times(1)).handleMLServiceResponse(captor.capture());
        MLServiceResponse called = captor.getValue();
        assertEquals(eventId, called.getEventId());
        assertEquals(payload, called.getResult());

        // verify preparer and messaging called
        verify(singleServiceCompletionResponsePreparer, times(1)).prepareMLServiceResponseMessage(any(ServiceCompletionEvent.class));
        verify(messagingTemplate, times(1)).convertAndSend(eq(SINGLE_SERVICE_COMPLETION_TOPIC), eq(response));
    }

    @Test
    void onConsortiumServiceResponseMessage_malformedPayload_isIgnored() {
        String badPayload = "not-a-json";

        // Act: should not throw
        listener.onConsortiumServiceResponseMessage(badPayload);

        // Assert: nothing should be invoked when parsing fails
        verifyNoInteractions(fraudWorkflowService);
        verifyNoInteractions(messagingTemplate);
        verifyNoInteractions(singleServiceCompletionResponsePreparer);
        verifyNoInteractions(eventWSSessionMapper);
    }

    @Test
    void onRuleServiceResponseMessage_malformedPayload_isIgnored() {
        String badPayload = "{broken-json";

        // Act: should not throw
        listener.onRuleServiceResponseMessage(badPayload);

        // Assert: nothing should be invoked when parsing fails
        verifyNoInteractions(fraudWorkflowService);
        verifyNoInteractions(messagingTemplate);
        verifyNoInteractions(singleServiceCompletionResponsePreparer);
        verifyNoInteractions(eventWSSessionMapper);
    }

    @Test
    void onImageServiceResponseMessage_emptyPayload_isIgnored() {
        String emptyPayload = "";

        // Act: should not throw
        listener.onImageServiceResponseMessage(emptyPayload);

        // Assert: nothing should be invoked
        verifyNoInteractions(fraudWorkflowService);
        verifyNoInteractions(messagingTemplate);
        verifyNoInteractions(singleServiceCompletionResponsePreparer);
        verifyNoInteractions(eventWSSessionMapper);
    }
}

