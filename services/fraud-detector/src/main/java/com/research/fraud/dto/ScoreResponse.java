package com.research.fraud.dto;

import java.util.List;
import java.util.Map;

public record ScoreResponse(
        double score,
        List<String> reasonCodes,
        Map<String, Object> explanation,
        int latencyMs
) {}
