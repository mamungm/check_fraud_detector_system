package com.research.fraud.dto;

import com.research.fraud.db.entity.DepositEvent;
import com.research.fraud.statemachine.WorkflowState;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class DepositEventDTO {
    private UUID eventId;
    private UUID institutionId;
    private UUID clearingInstitutionId;
    private DepositEvent.Channel channel;
    private OffsetDateTime depositTimestamp;
    private BigDecimal amount;
    private String currency = "CAD";
    private String accountToken;
    private String payeeToken;
    private String payorToken;
    private String deviceToken;
    private String region;
    private String checkSerialHash;
    private String micrRoutingHash;
    private String micrAccountHash;
    private String imageFrontUri;
    private String imageBackUri;
    private WorkflowState workflow;
    private float finalFraudProbability;
    private OffsetDateTime createdAt;
}
