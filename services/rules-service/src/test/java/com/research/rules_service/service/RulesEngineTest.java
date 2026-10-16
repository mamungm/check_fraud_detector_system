package com.research.rules_service.service;

import com.research.rules_service.config.RulesProperties;
import com.research.rules_service.db.entity.DepositEvent;
import com.research.rules_service.db.repo.DepositEventRepository;
import com.research.rules_service.dto.RulesServiceRequest;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RulesEngineTest {

    @Mock
    DepositEventRepository repo;

    RulesEngine rulesEngine;

    @BeforeEach
    void setup() {
        rulesEngine = new RulesEngine(repo, new RulesProperties(null, null, null, null));
    }

    private RulesServiceRequest request(BigDecimal amount, String account, String device,
                                        String serial, String routing, String micrAccount) {
        return new RulesServiceRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), DepositEvent.Channel.mobile,
                OffsetDateTime.now(), amount, "CAD", account, "payee-1", "payor-1", device, "region",
                serial, routing, micrAccount, "front", "back", false, null,
                OffsetDateTime.now(), OffsetDateTime.now());
    }

    private void stubCounts(RulesServiceRequest req, long dup, long acct7d, long deviceSeen) {
        lenient().when(repo.countByMicrRoutingHashAndMicrAccountHashAndCheckSerialHashAndEventIdNotAndDepositTimestampAfter(
                eq(req.micrRoutingHash()), eq(req.micrAccountHash()), eq(req.checkSerialHash()), eq(req.eventId()), any()))
                .thenReturn(dup);
        lenient().when(repo.countByAccountTokenAndEventIdNotAndDepositTimestampAfter(
                eq(req.accountToken()), eq(req.eventId()), any())).thenReturn(acct7d);
        lenient().when(repo.countByAccountTokenAndDeviceTokenAndEventIdNotAndDepositTimestampAfter(
                eq(req.accountToken()), eq(req.deviceToken()), eq(req.eventId()), any())).thenReturn(deviceSeen);
    }

    @Test
    void evaluate_triggersAllRules() {
        RulesServiceRequest req = request(BigDecimal.valueOf(6000), "acct-1", "device-1", "serial", "routing", "account");
        stubCounts(req, 1, 11, 0);

        Map<String, Object> hits = rulesEngine.evaluate(req);

        assertEquals(4, hits.size());
        assertEquals("DUPLICATE_PRESENTMENT", ((Map<?, ?>) hits.get("rule_duplicate_presentment")).get("ruleCode"));
        assertEquals("CRITICAL", ((Map<?, ?>) hits.get("rule_duplicate_presentment")).get("severity"));
        assertTrue(hits.containsKey("rule_high_deposit_velocity_7d"));
        assertTrue(hits.containsKey("rule_new_device_for_account"));
        assertEquals("HIGH_AMOUNT_CHECK", ((Map<?, ?>) hits.get("rule_high_amount_check")).get("ruleCode"));
    }

    @Test
    void evaluate_cleanDepositHasNoHits() {
        RulesServiceRequest req = request(BigDecimal.valueOf(420), "acct-1", "device-1", "serial", "routing", "account");
        stubCounts(req, 0, 2, 3);

        assertTrue(rulesEngine.evaluate(req).isEmpty());
    }

    @Test
    void evaluate_velocityNeedsMoreThanTenEarlierDeposits() {
        RulesServiceRequest req = request(BigDecimal.valueOf(100), "acct-1", "device-1", "serial", "routing", "account");
        stubCounts(req, 0, 10, 1);
        assertFalse(rulesEngine.evaluate(req).containsKey("rule_high_deposit_velocity_7d"));

        stubCounts(req, 0, 11, 1);
        assertTrue(rulesEngine.evaluate(req).containsKey("rule_high_deposit_velocity_7d"));
    }

    @Test
    void evaluate_amountThresholdIsInclusive() {
        RulesServiceRequest req = request(BigDecimal.valueOf(5000), "acct-1", "device-1", "serial", "routing", "account");
        stubCounts(req, 0, 0, 1);
        assertTrue(rulesEngine.evaluate(req).containsKey("rule_high_amount_check"));
    }

    @Test
    void evaluate_missingMicrHashesSkipDuplicateRule() {
        RulesServiceRequest req = request(BigDecimal.valueOf(100), "acct-1", "device-1", null, null, null);
        stubCounts(req, 5, 0, 1);

        Map<String, Object> hits = rulesEngine.evaluate(req);

        assertFalse(hits.containsKey("rule_duplicate_presentment"));
        verify(repo, never()).countByMicrRoutingHashAndMicrAccountHashAndCheckSerialHashAndEventIdNotAndDepositTimestampAfter(
                any(), any(), any(), any(), any());
    }

    @Test
    void evaluate_nullFieldsDoNotThrow() {
        RulesServiceRequest req = request(null, null, null, null, null, null);

        assertDoesNotThrow(() -> assertTrue(rulesEngine.evaluate(req).isEmpty()));
    }
}
