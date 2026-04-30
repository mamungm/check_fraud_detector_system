package com.research.fraud.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ServiceCompletionEvent(
        UUID eventId,
        String service,
        String status,
        Integer latencyMs,
        Double ts,
        String error,
        Map<String, Object> details
) {
}

