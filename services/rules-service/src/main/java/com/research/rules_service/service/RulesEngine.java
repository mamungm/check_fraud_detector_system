package com.research.rules_service.service;

import com.research.rules_service.config.RulesProperties;
import com.research.rules_service.db.repo.DepositEventRepository;
import com.research.rules_service.dto.RulesServiceRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RulesEngine {
    private final DepositEventRepository depositEventRepository;
    private final RulesProperties props;

    public Map<String, Object> evaluate(RulesServiceRequest e) {
        Map<String, Object> hits = new HashMap<>();
        OffsetDateTime now = OffsetDateTime.now();

        // Rule 1: the same check (routing + account + serial) was deposited before.
        // Skipped when any hash is missing: a null parameter would match every other deposit that lacks hashes.
        if (e.micrRoutingHash() != null && e.micrAccountHash() != null && e.checkSerialHash() != null) {
            int dup = Math.toIntExact(depositEventRepository
                    .countByMicrRoutingHashAndMicrAccountHashAndCheckSerialHashAndEventIdNotAndDepositTimestampAfter(
                            e.micrRoutingHash(), e.micrAccountHash(), e.checkSerialHash(), e.eventId(),
                            now.minusDays(props.duplicateLookbackDays())));
            if (dup > 0) {
                hits.put("rule_duplicate_presentment",
                        hit("DUPLICATE_PRESENTMENT", "CRITICAL", Map.of("prior_matches", dup)));
            }
        }

        if (e.accountToken() != null) {
            // Rule 2: more than N earlier deposits for this account in the last 7 days.
            int acct7d = Math.toIntExact(depositEventRepository
                    .countByAccountTokenAndEventIdNotAndDepositTimestampAfter(
                            e.accountToken(), e.eventId(), now.minusDays(7)));
            if (acct7d > props.velocityMaxDeposits7d()) {
                hits.put("rule_high_deposit_velocity_7d",
                        hit("HIGH_DEPOSIT_VELOCITY_7D", "HIGH", Map.of("deposit_count_7d", acct7d)));
            }

            // Rule 3: this account has no earlier deposit from this device.
            if (e.deviceToken() != null) {
                int seenBefore = Math.toIntExact(depositEventRepository
                        .countByAccountTokenAndDeviceTokenAndEventIdNotAndDepositTimestampAfter(
                                e.accountToken(), e.deviceToken(), e.eventId(),
                                now.minusDays(props.newDeviceLookbackDays())));
                if (seenBefore == 0) {
                    hits.put("rule_new_device_for_account",
                            hit("NEW_DEVICE_FOR_ACCOUNT", "MEDIUM", Map.of("device_token", e.deviceToken())));
                }
            }
        }

        // Rule 4: high amount.
        if (e.amount() != null && e.amount().doubleValue() >= props.highAmountThreshold()) {
            hits.put("rule_high_amount_check",
                    hit("HIGH_AMOUNT_CHECK", "MEDIUM", Map.of("amount", e.amount().doubleValue())));
        }

        log.info("hits - {}", hits);
        return hits;
    }

    private Map<String, Object> hit(String code, String severity, Map<String, Object> evidence) {
        return Map.of("ruleCode", code, "severity", severity, "evidence", evidence);
    }
}
