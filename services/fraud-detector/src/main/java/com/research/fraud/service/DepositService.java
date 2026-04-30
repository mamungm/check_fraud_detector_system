package com.research.fraud.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.research.fraud.db.entity.*;
import com.research.fraud.db.repo.*;
import com.research.fraud.dto.DepositEventRequest;
import com.research.fraud.dto.ScoreResponse;
import com.research.fraud.kafka.DepositProcessingTracker;
import com.research.fraud.mappers.DepositEventMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
public class DepositService {
    private final DepositEventRepository depositEventRepository;
    private final RuleHitRepository ruleHitRepository;
    private final ModelScoreRepository modelScoreRepository;
    private final FraudAlertRepository fraudAlertRepository;
    private final FraudCaseRepository fraudCaseRepository;
    private final KafkaTemplate<String, String> kafka;
    private final ExternalScoringClient scoring;
    private final RulesEngine rules;
    private final DepositEventMapper depositEventMapper;
    private final ObjectMapper objectMapper;
    private final DepositProcessingTracker processingTracker;

    public DepositService(DepositEventRepository depositEventRepository, RuleHitRepository ruleHitRepository, ModelScoreRepository modelScoreRepository,
                          FraudAlertRepository fraudAlertRepository, FraudCaseRepository fraudCaseRepository, KafkaTemplate<String, String> kafka,
                          ExternalScoringClient scoring, RulesEngine rules, DepositEventMapper depositEventMapper, ObjectMapper objectMapper,
                          DepositProcessingTracker processingTracker) {
        this.depositEventRepository = depositEventRepository;
        this.ruleHitRepository = ruleHitRepository;
        this.modelScoreRepository = modelScoreRepository;
        this.fraudAlertRepository = fraudAlertRepository;
        this.fraudCaseRepository = fraudCaseRepository;
        this.kafka = kafka;
        this.scoring = scoring;
        this.rules = rules;
        this.depositEventMapper = depositEventMapper;
        this.objectMapper = objectMapper;
        this.processingTracker = processingTracker;
    }

    public Map<String, Object> ingest(DepositEventRequest depositEventRequest) throws Exception {
        DepositEvent depositEvent = depositEventMapper.toEntity(depositEventRequest);
        depositEvent = depositEventRepository.save(depositEvent);

        // Track async downstream processing (Kafka fan-out). This is independent of the
        // synchronous HTTP scoring calls below.
        processingTracker.register(depositEvent.getEventId());
        kafka.send("check.deposit.created", depositEvent.getEventId().toString(),
                objectMapper.writeValueAsString(depositEvent));

        List<Map<String, Object>> ruleHits = rules.evaluate(depositEvent);
        ArrayList<RuleHit> ruleHitList = new ArrayList<>();
        for (Map<String, Object> h : ruleHits) {
            RuleHit ruleHit = RuleHit.builder()
                    .eventId(depositEvent.getEventId())
                    .ruleCode((String) h.get("ruleCode"))
                    .severity(RuleHit.Severity.valueOf((String) h.get("severity")))
                    .evidence(objectMapper.valueToTree(h.get("evidence")))
                    .build();
            ruleHitList.add(ruleHit);
        }
        ruleHitRepository.saveAll(ruleHitList);

        ScoreResponse ml = safe(() -> scoring.mlScore(depositEventRequest), "ml failed");
        ScoreResponse image = safe(() -> scoring.imageScore(depositEventRequest), "image failed");
        ScoreResponse consortium = safe(() -> scoring.consortiumScore(depositEventRequest), "consortium failed");

        double ruleScore = ruleHits.stream().mapToDouble(h -> switch ((String) h.get("severity")) {
            case "CRITICAL" -> 1.0;
            case "HIGH" -> 0.75;
            case "MEDIUM" -> 0.45;
            default -> 0.2;
        }).max().orElse(0.0);

        double finalScore = Math.max(ruleScore, 0.45 * ml.score() + 0.25 * image.score() + 0.30 * consortium.score());

        Set<String> reasons = new LinkedHashSet<>();
        ruleHits.forEach(h -> reasons.add((String) h.get("ruleCode")));
        reasons.addAll(ml.reasonCodes());
        reasons.addAll(image.reasonCodes());
        reasons.addAll(consortium.reasonCodes());

        String decision = finalScore >= 0.85 || reasons.contains("DUPLICATE_PRESENTMENT") ? "HOLD"
                : finalScore >= 0.60 ? "MANUAL_REVIEW" : "PASS";

        Map<String, Object> explanation = Map.of(
                "ruleHits", ruleHits,
                "ml", ml.explanation(),
                "image", image.explanation(),
                "consortium", consortium.explanation(),
                "scoreBlend", Map.of("ruleMax", ruleScore, "ml", ml.score(), "image", image.score(), "consortium", consortium.score())
        );

        int latencyMs = ml.latencyMs() + image.latencyMs() + consortium.latencyMs();
        ModelScore modelScore = ModelScore.builder()
                .eventId(depositEvent.getEventId())
                .modelName("ensemble-demo")
                .modelVersion("0.1.0")
                .score(BigDecimal.valueOf(finalScore))
                .explanation(objectMapper.valueToTree(explanation))
                .latencyMs(latencyMs)
                .build();
        modelScoreRepository.save(modelScore);

        FraudAlert fraudAlert = FraudAlert.builder()
                .eventId(depositEvent.getEventId())
                .riskScore(BigDecimal.valueOf(finalScore))
                .decision(FraudAlert.Decision.valueOf(decision))
                .reasonCodes(List.of(reasons.toArray(new String[0])))
                .explanation(objectMapper.valueToTree(explanation))
                .build();
        fraudAlertRepository.save(fraudAlert);

        if (!decision.equals("PASS")) {
            FraudCase fraudCase = FraudCase.builder()
                    .alertId(fraudAlert.getAlertId())
                    .priority(FraudCase.Priority.valueOf(finalScore >= 0.85 ? "HIGH" : "MEDIUM"))
                    .build();
            fraudCaseRepository.save(fraudCase);
        }

        return Map.of("eventId", depositEvent.getEventId(), "riskScore", finalScore, "decision", decision,
                "reasonCodes", reasons, "explanation", explanation);
    }

    private ScoreResponse safe(ScoreSupplier s, String reason) {
        try {
            return s.get();
        } catch (Exception ex) {
            return scoring.fallback(reason);
        }
    }

    private interface ScoreSupplier {
        ScoreResponse get();
    }
}
