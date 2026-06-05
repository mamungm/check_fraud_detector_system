package com.research.fraud.service;

import com.research.fraud.db.entity.DepositEvent;
import com.research.fraud.db.repo.DepositEventRepository;
import com.research.fraud.dto.DepositEventResponse;
import com.research.fraud.dto.ServiceCompletionEvent;
import com.research.fraud.mappers.DepositEventDTOMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SingleServiceCompletionResponsePreparerTest {

    @Mock
    DepositEventRepository depositEventRepository;

    @Mock
    DepositEventDTOMapper depositEventDtoMapper;

    SingleServiceCompletionResponsePreparer preparer;

    @BeforeEach
    void setUp() {
        preparer = new SingleServiceCompletionResponsePreparer(depositEventRepository, depositEventDtoMapper);
    }

    @Test
    void prepareConsortiumServiceResponseMessage_fetchesDepositAndBuildsResponseWithExpectedMessage() {
        UUID eventId = UUID.randomUUID();
        ServiceCompletionEvent evt = new ServiceCompletionEvent(eventId, "consortium", "OK", null, null, null, null);

        DepositEvent depositEvent = DepositEvent.builder()
                .eventId(eventId)
                .depositTimestamp(OffsetDateTime.now())
                .amount(new BigDecimal("10.00"))
                .currency("CAD")
                .build();

        DepositEventResponse expected = DepositEventResponse.builder()
                .depositEventList(Collections.emptyList())
                .message("consortium service completed")
                .build();

        when(depositEventRepository.findByEventId(eq(eventId))).thenReturn(List.of(depositEvent));
        when(depositEventDtoMapper.buildDepositResponse(eq(depositEvent), eq("consortium service completed")))
                .thenReturn(expected);

        DepositEventResponse actual = preparer.prepareConsortiumServiceResponseMessage(evt);

        assertSame(expected, actual);
        verify(depositEventRepository, times(1)).findByEventId(eventId);
        verify(depositEventDtoMapper, times(1)).buildDepositResponse(depositEvent, "consortium service completed");
    }

    @Test
    void prepareImageServiceResponseMessage_fetchesDepositAndBuildsResponseWithExpectedMessage() {
        UUID eventId = UUID.randomUUID();
        ServiceCompletionEvent evt = new ServiceCompletionEvent(eventId, "image", "OK", null, null, null, null);

        DepositEvent depositEvent = DepositEvent.builder()
                .eventId(eventId)
                .depositTimestamp(OffsetDateTime.now())
                .amount(new BigDecimal("20.00"))
                .currency("CAD")
                .build();

        DepositEventResponse expected = DepositEventResponse.builder()
                .depositEventList(Collections.emptyList())
                .message("image service completed")
                .build();

        when(depositEventRepository.findByEventId(eq(eventId))).thenReturn(List.of(depositEvent));
        when(depositEventDtoMapper.buildDepositResponse(eq(depositEvent), eq("image service completed")))
                .thenReturn(expected);

        DepositEventResponse actual = preparer.prepareImageServiceResponseMessage(evt);

        assertSame(expected, actual);
        verify(depositEventRepository, times(1)).findByEventId(eventId);
        verify(depositEventDtoMapper, times(1)).buildDepositResponse(depositEvent, "image service completed");
    }

    @Test
    void prepareMLServiceResponseMessage_fetchesDepositAndBuildsResponseWithExpectedMessage() {
        UUID eventId = UUID.randomUUID();
        ServiceCompletionEvent evt = new ServiceCompletionEvent(eventId, "ml", "OK", null, null, null, null);

        DepositEvent depositEvent = DepositEvent.builder()
                .eventId(eventId)
                .depositTimestamp(OffsetDateTime.now())
                .amount(new BigDecimal("30.00"))
                .currency("CAD")
                .build();

        DepositEventResponse expected = DepositEventResponse.builder()
                .depositEventList(Collections.emptyList())
                .message("ml service completed")
                .build();

        when(depositEventRepository.findByEventId(eq(eventId))).thenReturn(List.of(depositEvent));
        when(depositEventDtoMapper.buildDepositResponse(eq(depositEvent), eq("ml service completed")))
                .thenReturn(expected);

        DepositEventResponse actual = preparer.prepareMLServiceResponseMessage(evt);

        assertSame(expected, actual);
        verify(depositEventRepository, times(1)).findByEventId(eventId);
        verify(depositEventDtoMapper, times(1)).buildDepositResponse(depositEvent, "ml service completed");
    }

    @Test
    void prepareRuleServiceResponseMessage_fetchesDepositAndBuildsResponseWithExpectedMessage() {
        UUID eventId = UUID.randomUUID();
        ServiceCompletionEvent evt = new ServiceCompletionEvent(eventId, "rule", "OK", null, null, null, null);

        DepositEvent depositEvent = DepositEvent.builder()
                .eventId(eventId)
                .depositTimestamp(OffsetDateTime.now())
                .amount(new BigDecimal("40.00"))
                .currency("CAD")
                .build();

        DepositEventResponse expected = DepositEventResponse.builder()
                .depositEventList(Collections.emptyList())
                .message("rule service completed")
                .build();

        when(depositEventRepository.findByEventId(eq(eventId))).thenReturn(List.of(depositEvent));
        when(depositEventDtoMapper.buildDepositResponse(eq(depositEvent), eq("rule service completed")))
                .thenReturn(expected);

        DepositEventResponse actual = preparer.prepareRuleServiceResponseMessage(evt);

        assertSame(expected, actual);
        verify(depositEventRepository, times(1)).findByEventId(eventId);
        verify(depositEventDtoMapper, times(1)).buildDepositResponse(depositEvent, "rule service completed");
    }

    @Test
    void prepareRuleServiceResponseMessage_whenRepoReturnsEmptyList_throws() {
        UUID eventId = UUID.randomUUID();
        ServiceCompletionEvent evt = new ServiceCompletionEvent(eventId, "rule", "OK", null, null, null, null);

        when(depositEventRepository.findByEventId(eq(eventId))).thenReturn(Collections.emptyList());

        assertThrows(RuntimeException.class, () -> preparer.prepareRuleServiceResponseMessage(evt));
        verify(depositEventRepository, times(1)).findByEventId(eventId);
        verifyNoInteractions(depositEventDtoMapper);
    }
}

