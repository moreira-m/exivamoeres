package com.exivamoeres.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuração dos times: tamanho máximo, limites e prazos por plano, e o
 * intervalo do job que arquiva times expirados. Interpretado por PlanPolicy.
 */
@ConfigurationProperties(prefix = "app.team")
public record TeamProperties(
        int maxMembers,
        /** Máximo de times ATIVOS simultâneos que uma conta FREE pode ter. */
        int freeActiveLimit,
        int freeDurationDays,
        int premiumDurationDays,
        Duration expirationCheckInterval
) {
    public Duration freeDuration() {
        return Duration.ofDays(freeDurationDays);
    }

    public Duration premiumDuration() {
        return Duration.ofDays(premiumDurationDays);
    }
}
