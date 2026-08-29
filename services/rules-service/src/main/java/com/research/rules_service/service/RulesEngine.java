package com.research.rules_service.service;

import com.research.rules_service.db.repo.DepositEventRepository;
import com.research.rules_service.dto.RulesServiceRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RulesEngine {
    private final DepositEventRepository depositEventRepository;

    public Map<String, Object> evaluate(RulesServiceRequest e) {
        Map<String, Object> hits = new HashMap<>();

        int dup = Math.toIntExact(depositEventRepository
                .countByMicrRoutingHashAndMicrAccountHashAndCheckSerialHashAndEventIdNot(
                        e.micrRoutingHash(),
                        e.micrAccountHash(),
                        e.checkSerialHash(),
                        e.eventId()
                ));

        if (dup > 0) {
            hits.put("rule_duplicate_presentment", hit("DUPLICATE_PRESENTMENT", "CRITICAL", Map.of("prior_matches", dup)));
        }

        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(7);
        int acct7d = Math.toIntExact(depositEventRepository
                .countByAccountTokenAndDepositTimestampAfter(e.accountToken(), cutoff));

        if (acct7d > 10) {
            hits.put("rule_high_deposit_velocity_7d", hit("HIGH_DEPOSIT_VELOCITY_7D", "HIGH", Map.of("deposit_count_7d", acct7d)));
        }

        cutoff = OffsetDateTime.now().minusDays(90);
        int newDevice = Math.toIntExact(depositEventRepository.countByAccountTokenAndDeviceTokenAndDepositTimestampAfter
                (e.accountToken(), e.deviceToken(), cutoff));

        if (newDevice == 0) {
            hits.put("rule_new_device_for_account", hit("NEW_DEVICE_FOR_ACCOUNT", "MEDIUM", Map.of("device_token", e.deviceToken())));
        }

        if (e.amount().doubleValue() >= 5000.0) {
            hits.put("rule_high_amount_check", hit("HIGH_AMOUNT_CHECK", "MEDIUM", Map.of("amount", e.amount().doubleValue())));
        }

        log.info("hits - " + hits);

        return hits;
    }

    private Map<String, Object> hit(String code, String severity, Map<String, Object> evidence) {
        return Map.of("ruleCode", code, "severity", severity, "evidence", evidence);
    }
}
