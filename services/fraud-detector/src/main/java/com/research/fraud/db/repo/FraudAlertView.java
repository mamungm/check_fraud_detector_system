package com.research.fraud.db.repo;

import java.util.UUID;

public interface FraudAlertView {
    UUID getAlertId();

    UUID getEventId();

    java.math.BigDecimal getRiskScore();

    String getDecision();

    java.util.List<String> getReasonCodes();

    String getStatus();

    java.time.OffsetDateTime getCreatedAt();
}
