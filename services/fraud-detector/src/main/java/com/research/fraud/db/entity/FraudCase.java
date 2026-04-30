package com.research.fraud.db.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(
        indexes = {
                @Index(name = "idx_fraud_case_alert", columnList = "alert_id"),
                @Index(name = "idx_fraud_case_status", columnList = "status"),
                @Index(name = "idx_fraud_case_priority", columnList = "priority")
        }
)
public class FraudCase {
    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID caseId;

    private UUID alertId;

    private String assignedTo;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private CaseStatus status = CaseStatus.NEW;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Priority priority = Priority.MEDIUM;

    @Column(updatable = false)
    private OffsetDateTime createdAt;

    private OffsetDateTime closedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (status == null) {
            status = CaseStatus.NEW;
        }
        if (priority == null) {
            priority = Priority.MEDIUM;
        }
    }

    public enum CaseStatus {
        NEW,
        IN_PROGRESS,
        UNDER_REVIEW,
        CLOSED,
        ESCALATED
    }

    public enum Priority {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
}
