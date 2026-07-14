package com.exivamoeres.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Regras temporais do fluxo de claim (polling de 15min, expiração de 24h). */
@ConfigurationProperties(prefix = "app.claim")
public record ClaimProperties(
        Duration pollInterval,
        int expirationHours
) {
    public Duration expiration() {
        return Duration.ofHours(expirationHours);
    }
}
