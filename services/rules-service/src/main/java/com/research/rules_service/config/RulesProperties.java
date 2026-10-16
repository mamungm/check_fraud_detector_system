package com.research.rules_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Rule thresholds. Override in application.yaml under the "rules" key.
 */
@ConfigurationProperties(prefix = "rules")
public record RulesProperties(
        Integer velocityMaxDeposits7d,
        Integer newDeviceLookbackDays,
        Double highAmountThreshold,
        Integer duplicateLookbackDays
) {
    public RulesProperties {
        if (velocityMaxDeposits7d == null) velocityMaxDeposits7d = 10;
        if (newDeviceLookbackDays == null) newDeviceLookbackDays = 90;
        if (highAmountThreshold == null) highAmountThreshold = 5000.0;
        if (duplicateLookbackDays == null) duplicateLookbackDays = 365;
    }
}
