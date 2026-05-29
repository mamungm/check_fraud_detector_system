package com.research.fraud.statemachine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tbl_fraud_detection_workflow")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudDetectionWorkflowEntity {
    @Id
    private UUID eventId;

    private boolean imageCompleted;

    private boolean consortiumCompleted;

    private boolean mlCompleted;

    private boolean ruleCompleted;

    @Column(columnDefinition = "TEXT")
    private String imageResult;

    @Column(columnDefinition = "TEXT")
    private String consortiumResult;

    @Column(columnDefinition = "TEXT")
    private String mlResult;

    @Column(columnDefinition = "TEXT")
    private String ruleResult;

    private Instant createdAt;

    private Instant updatedAt;
}
