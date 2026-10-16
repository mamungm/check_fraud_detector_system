package com.research.fraud.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * The stored workflow row of one deposit: completion flags and each service's full response (parsed JSON as plain maps, so it serialises the same under any Jackson version).
 * A result is null when that service has not responded yet.
 */
@Data
@Builder
public class WorkflowDetailsDTO {
    private UUID eventId;

    private boolean consortiumCompleted;
    private boolean imageCompleted;
    private boolean ruleCompleted;
    private boolean mlCompleted;

    private Map<String, Object> consortiumResult;
    private Map<String, Object> imageResult;
    private Map<String, Object> ruleResult;
    private Map<String, Object> mlResult;

    private Instant createdAt;
    private Instant updatedAt;
}
