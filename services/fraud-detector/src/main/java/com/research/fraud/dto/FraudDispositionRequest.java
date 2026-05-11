package com.research.fraud.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record FraudDispositionRequest(
        UUID eventId,
        EnumFraudDisposition fraudDisposition
) {
}
