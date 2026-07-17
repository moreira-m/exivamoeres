package com.exivamoeres.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

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
        Duration expirationCheckInterval,
        /**
         * Nomes de personagem isentos da exigência de Premium Account (uso
         * administrativo/testes). Vazio em produção normal. Case-insensitive.
         */
        List<String> premiumBypassCharacters
) {
    public Duration freeDuration() {
        return Duration.ofDays(freeDurationDays);
    }

    public Duration premiumDuration() {
        return Duration.ofDays(premiumDurationDays);
    }

    /** Verdadeiro se o personagem está isento da exigência de Premium Account. */
    public boolean isPremiumBypassed(String characterName) {
        if (characterName == null || premiumBypassCharacters == null) {
            return false;
        }
        return premiumBypassCharacters.stream()
                .anyMatch(name -> name != null && name.trim().equalsIgnoreCase(characterName.trim()));
    }
}
