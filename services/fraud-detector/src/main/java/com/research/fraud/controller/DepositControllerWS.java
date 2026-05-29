package com.research.fraud.controller;

import com.research.fraud.config.Constants;
import com.research.fraud.dto.DepositEventRequest;
import com.research.fraud.dto.DepositEventResponse;
import com.research.fraud.service.DepositService;
import com.research.fraud.service.EventWSSessionMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
@AllArgsConstructor
@Slf4j
public class DepositControllerWS implements Constants {
    private final DepositService service;
    private final EventWSSessionMapper eventWSSessionMapper;

    @MessageMapping("/deposit_request")
    @SendTo(DEPOSIT_RESPONSE_TOPIC)
    public DepositEventResponse depositRequest(@Payload DepositEventRequest request,
                                               SimpMessageHeaderAccessor headerAccessor) throws Exception {
        log.info("deposit_request message received with request = {}", request);
        String sessionId = headerAccessor.getSessionId();
        DepositEventResponse response = service.ingest(request);
        eventWSSessionMapper.mapEventToWSSession(response.eventId(), sessionId);

        return response;
    }
}
