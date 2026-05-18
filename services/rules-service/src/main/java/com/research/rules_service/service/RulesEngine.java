package com.research.rules_service.service;

import com.research.rules_service.db.entity.DepositEvent;
import com.research.rules_service.db.repo.DepositEventRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class RulesEngine {
    private final DepositEventRepository depositEventRepository;

    public RulesEngine(DepositEventRepository depositEventRepository) {
        this.depositEventRepository = depositEventRepository;
    }

    public List<Map<String, Object>> evaluate(DepositEvent e) {
        List<Map<String, Object>> hits = new ArrayList<>();

        int dup = Math.toIntExact(depositEventRepository
                .countByMicrRoutingHashAndMicrAccountHashAndCheckSerialHashAndEventIdNot(
                        e.getMicrRoutingHash(),
                        e.getMicrAccountHash(),
                        e.getCheckSerialHash(),
                        e.getEventId()
                ));

        if (dup > 0) {
            hits.add(hit("DUPLICATE_PRESENTMENT", "CRITICAL", Map.of("prior_matches", dup)));
        }

        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(7);
        int acct7d = Math.toIntExact(depositEventRepository.countByAccountTokenAndDepositTimestampAfter(e.getAccountToken(),
                cutoff));

        if (acct7d > 10) {
            hits.add(hit("HIGH_DEPOSIT_VELOCITY_7D", "HIGH", Map.of("deposit_count_7d", acct7d)));
        }

        cutoff = OffsetDateTime.now().minusDays(90);
        int newDevice = Math.toIntExact(depositEventRepository.countByAccountTokenAndDeviceTokenAndDepositTimestampAfter
                (e.getAccountToken(), e.getDeviceToken(), cutoff));

        if (newDevice == 0) {
            hits.add(hit("NEW_DEVICE_FOR_ACCOUNT", "MEDIUM", Map.of("device_token", e.getDeviceToken())));
        }

        if (e.getAmount().doubleValue() >= 5000.0) {
            hits.add(hit("HIGH_AMOUNT_CHECK", "MEDIUM", Map.of("amount", e.getAmount().doubleValue())));
        }

        return hits;
    }

    private Map<String, Object> hit(String code, String severity, Map<String, Object> evidence) {
        return Map.of("ruleCode", code, "severity", severity, "evidence", evidence);
    }
}
