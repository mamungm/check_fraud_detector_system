package com.research.fraud.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record DepositEventResponse(
        List<DepositEventDTO> depositEventList,
        String message
) {
}
