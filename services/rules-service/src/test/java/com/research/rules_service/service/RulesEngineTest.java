package com.research.rules_service.service;

import com.research.rules_service.db.repo.DepositEventRepository;
import com.research.rules_service.dto.RulesServiceRequest;
import com.research.rules_service.db.entity.DepositEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RulesEngineTest {

    @Mock
    DepositEventRepository repo;

    RulesEngine rulesEngine;

    @BeforeEach
    void setup() {
        rulesEngine = new RulesEngine(repo);
    }

    @Test
    void evaluate_triggersExpectedRules() {
        UUID eventId = UUID.randomUUID();
        // Prepare request with high amount to trigger high amount rule
        RulesServiceRequest req = new RulesServiceRequest(
                eventId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                DepositEvent.Channel.mobile,
                OffsetDateTime.now(),
                BigDecimal.valueOf(6000.00),
                "CAD",
                "acct-1",
                "payee-1",
                "payor-1",
                "device-1",
                "region",
                "serial-hash",
                "routing-hash",
                "account-hash",
                "front",
                "back",
                false,
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        // Configure repository to indicate duplicates and high velocity, and new device
        when(repo.countByMicrRoutingHashAndMicrAccountHashAndCheckSerialHashAndEventIdNot(
                req.micrRoutingHash(), req.micrAccountHash(), req.checkSerialHash(), req.eventId()
        )).thenReturn(1L);

        // Use argument matchers for time parameters since RulesEngine computes cutoffs internally
        when(repo.countByAccountTokenAndDepositTimestampAfter(org.mockito.ArgumentMatchers.eq(req.accountToken()), org.mockito.ArgumentMatchers.any())).thenReturn(11L);

        when(repo.countByAccountTokenAndDeviceTokenAndDepositTimestampAfter(org.mockito.ArgumentMatchers.eq(req.accountToken()), org.mockito.ArgumentMatchers.eq(req.deviceToken()), org.mockito.ArgumentMatchers.any())).thenReturn(0L);

        Map<String, Object> hits = rulesEngine.evaluate(req);

        assertTrue(hits.containsKey("rule_duplicate_presentment"));
        assertTrue(hits.containsKey("rule_high_deposit_velocity_7d"));
        assertTrue(hits.containsKey("rule_new_device_for_account"));
        assertTrue(hits.containsKey("rule_high_amount_check"));

        Map<String, Object> dupHit = (Map<String, Object>) hits.get("rule_duplicate_presentment");
        assertEquals("DUPLICATE_PRESENTMENT", dupHit.get("ruleCode"));

        Map<String, Object> amountHit = (Map<String, Object>) hits.get("rule_high_amount_check");
        assertEquals("HIGH_AMOUNT_CHECK", amountHit.get("ruleCode"));
    }
}


