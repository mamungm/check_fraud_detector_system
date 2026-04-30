package com.research.fraud.dto;

import com.research.fraud.db.entity.DepositEvent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DepositEventRequest(
        UUID eventId,
        UUID institutionId,
        DepositEvent.Channel channel,
        OffsetDateTime depositTimestamp,
        BigDecimal amount,
        String currency,
        String accountToken,
        String payeeToken,
        String deviceToken,
        String region,
        String checkSerial,
        String micrRoutingHash,
        String micrAccountHash,
        String imageFrontUri,
        String imageBackUri
) {}
