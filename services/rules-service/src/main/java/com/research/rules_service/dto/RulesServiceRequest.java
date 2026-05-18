package com.research.rules_service.dto;

import com.research.rules_service.db.entity.DepositEvent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RulesServiceRequest(
        UUID institutionId,
        UUID clearingInstitutionId,
        DepositEvent.Channel channel,
        OffsetDateTime depositTimestamp,
        BigDecimal amount,
        String currency,
        String accountToken,
        String payeeToken,
        String payorToken,
        String deviceToken,
        String region,
        String checkSerialHash,
        String micrRoutingHash,
        String micrAccountHash,
        String imageFrontUri,
        String imageBackUri) {
}
