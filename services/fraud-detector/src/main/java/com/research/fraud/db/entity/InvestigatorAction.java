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
                @Index(name = "idx_investigator_action_case", columnList = "case_id"),
                @Index(name = "idx_investigator_action_actor", columnList = "actor"),
                @Index(name = "idx_investigator_action_created", columnList = "created_at")
        }
)
public class InvestigatorAction {
    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID actionId;

    private UUID caseId;

    @Column(nullable = false)
    private String actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActionType actionType;

    private String notes;

    @Enumerated(EnumType.STRING)
    private Disposition disposition;

    @Column(updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public enum ActionType {
        CREATE_CASE,
        ASSIGN,
        REASSIGN,
        ADD_NOTE,
        REQUEST_INFO,
        ESCALATE,
        CLOSE_CASE,
        REOPEN_CASE,
        DISPOSITION
    }

    public enum Disposition {
        FRAUD_CONFIRMED,
        FALSE_POSITIVE,
        CUSTOMER_ERROR,
        DUPLICATE,
        UNDER_INVESTIGATION,
        NO_ACTION
    }
}
