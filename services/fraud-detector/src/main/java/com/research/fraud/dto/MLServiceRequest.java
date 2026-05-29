package com.research.fraud.dto;

import com.research.fraud.db.entity.DepositEvent;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class MLServiceRequest {
    private UUID eventId;
    private String institutionId;
    private float amount;
    private DepositEvent.Channel channel;

    private float account_deposit_count_7d;
    private float account_avg_amount_30d;
    private float account_new_device;
    private float payee_seen_institutions_14d;
    private float payee_fraud_count_30d;
    private float device_fraud_count_30d;
    private float image_duplicate_score;
    private boolean ocr_amount_match;
    private float layout_anomaly_score;
    private float font_anomaly_score;
    private float signature_presence_score;
    private float endorsement_score;

    private float rule_hit_count;
    private float critical_rule_hit_count;

    private float return_code_present;
    private float chargeback_present;
    private float loss_amount;

    private float days_since_deposit;
    private boolean clearing_amount_matches_deposit;
    private float drawee_bank_prior_fraud_30d;
    private float deposit_to_clearing_bank_risk;
}