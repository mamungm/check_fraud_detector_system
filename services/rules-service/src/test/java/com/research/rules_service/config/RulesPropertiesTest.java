package com.research.rules_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Checks that the "rules" settings bind. Runs without a database or Kafka, unlike a full @SpringBootTest
 * (which needs the Postgres from docker compose and therefore fails on machines and CI runners without it).
 */
class RulesPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class);

    @EnableConfigurationProperties(RulesProperties.class)
    static class Config {
    }

    @Test
    void usesDefaultsWhenNothingIsConfigured() {
        runner.run(ctx -> {
            RulesProperties p = ctx.getBean(RulesProperties.class);
            assertEquals(10, p.velocityMaxDeposits7d());
            assertEquals(90, p.newDeviceLookbackDays());
            assertEquals(5000.0, p.highAmountThreshold());
            assertEquals(365, p.duplicateLookbackDays());
        });
    }

    @Test
    void bindsConfiguredValues() {
        runner.withPropertyValues(
                        "rules.velocity-max-deposits7d=3",
                        "rules.new-device-lookback-days=30",
                        "rules.high-amount-threshold=2500.5",
                        "rules.duplicate-lookback-days=60")
                .run(ctx -> {
                    RulesProperties p = ctx.getBean(RulesProperties.class);
                    assertEquals(3, p.velocityMaxDeposits7d());
                    assertEquals(30, p.newDeviceLookbackDays());
                    assertEquals(2500.5, p.highAmountThreshold());
                    assertEquals(60, p.duplicateLookbackDays());
                });
    }
}
