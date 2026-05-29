package com.research.fraud.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.research.fraud.db.entity.DepositEvent;
import com.research.fraud.db.repo.DepositEventRepository;
import com.research.fraud.dto.MLServiceRequest;
import com.research.fraud.statemachine.FraudDetectionWorkflowEntity;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@Service
@AllArgsConstructor
@Slf4j
public class MLServiceRequestDataPreparer {
    private final DepositEventRepository depositEventRepository;
    private final ObjectMapper objectMapper;

    public MLServiceRequest prepareMLServiceRequest(FraudDetectionWorkflowEntity workflow) throws JsonProcessingException {
        log.info("Preparing ML service request: workflow.getConsortiumResult() = {}", workflow.getConsortiumResult());
        DepositEvent depositEvent = depositEventRepository.getReferenceById(workflow.getEventId());
        ConsortiumResult consortiumResult = objectMapper.readValue(workflow.getConsortiumResult(), ConsortiumResult.class);
        ImageResult imageResult = objectMapper.readValue(workflow.getImageResult(), ImageResult.class);

        return MLServiceRequest.builder()
                .eventId(workflow.getEventId())
                .institutionId(depositEvent.getInstitutionId().toString())
                .amount(depositEvent.getAmount().floatValue())
                .channel(depositEvent.getChannel())

                .payee_seen_institutions_14d(consortiumResult.getDetails().getSupportingLinkedCounts().getPAYEE().getInstitutionCount14d())
                .payee_fraud_count_30d(consortiumResult.getDetails().getSupportingLinkedCounts().getPAYEE().getFraudCount30d())
                .device_fraud_count_30d(consortiumResult.getDetails().getSupportingLinkedCounts().getDEVICE().getFraudCount30d())

                .image_duplicate_score(imageResult.getDetails().getImage_duplicate_score())
                .ocr_amount_match(imageResult.getDetails().isOcr_amount_match())
                .layout_anomaly_score(imageResult.getDetails().getLayout_anomaly_score())
                .font_anomaly_score(imageResult.getDetails().getFont_anomaly_score())
                .signature_presence_score(imageResult.getDetails().getSignature_presence_score())
                .endorsement_score(imageResult.getDetails().getEndorsement_score())

                .account_deposit_count_7d(depositEventRepository.countDepositsByAccountTokenLast7Days(
                        depositEvent.getAccountToken(), OffsetDateTime.now(ZoneOffset.UTC).minusDays(7)))
                .account_avg_amount_30d(depositEventRepository.getAverageAmountLast30Days(
                        depositEvent.getAccountToken(), OffsetDateTime.now(ZoneOffset.UTC).minusDays(30)))
                .account_new_device(depositEventRepository.countDepositEventsByAccountTokenAndDeviceToken(
                        depositEvent.getAccountToken(), depositEvent.getDeviceToken()))
                .days_since_deposit(ChronoUnit.DAYS.between(depositEvent.getDepositTimestamp(), OffsetDateTime.now(ZoneOffset.UTC)))
                .clearing_amount_matches_deposit(true)
                .drawee_bank_prior_fraud_30d(depositEventRepository.countPriorFraudsForDraweeBank30d(
                        depositEvent.getInstitutionId(), OffsetDateTime.now(ZoneOffset.UTC).minusDays(30)))
                .deposit_to_clearing_bank_risk(consortiumResult.getDetails().getExplanation().getScoreBlend().getBankFlowRisk())

                .return_code_present(1.0f)
                .chargeback_present(1.0f)
                .loss_amount(1.0f)


                .rule_hit_count(1.0f)
                .critical_rule_hit_count(1.0f)
                .build();
    }
}
