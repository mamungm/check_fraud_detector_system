package com.research.rules_service.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class ServiceCompletionEvent {
    private UUID eventId;
    private String service;
    private String status;
    private Integer latencyMs;
    private Double ts;
    private String error;
    private List<Map<String, Object>> details;
}
