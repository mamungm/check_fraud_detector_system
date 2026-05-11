package com.research.fraud.db.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DepositEvent {
    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID eventId;

    private UUID institutionId;

    private UUID clearingInstitutionId;

    @Enumerated(EnumType.STRING)
    private Channel channel;

    @Column(nullable = false)
    private OffsetDateTime depositTimestamp;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Builder.Default
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

    @Builder.Default
    private String status = "RECEIVED";

    private OffsetDateTime createdAt;

    // --- Lifecycle hooks ---
    @PrePersist
    public void prePersist() {
        if (eventId == null) {
            eventId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    // --- Enum for channel ---
    public enum Channel {
        mobile,
        ATM,
        branch
    }
}
