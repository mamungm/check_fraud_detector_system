package com.research.fraud.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record FraudDispositionResponse(
        UUID eventId,
        EnumFraudDisposition fraudDisposition
) {
}
