package com.exivamoeres.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limites de uso por IP (RateLimitFilter) e por usuário (UserRateLimiter).
 *
 * Os dois primeiros protegem a criação de identidade — a porta de entrada de
 * qualquer abuso em massa. Os dois últimos protegem o que uma identidade já
 * autenticada pode fazer: encher a busca de times e martelar a TibiaData.
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        /** Tentativas de login/registro por minuto, por IP. */
        int authPerMinute,
        /** Contas anônimas criadas por hora, por IP. */
        int anonymousPerHour,
        /** Times criados por hora, por usuário. */
        int teamCreationPerHour,
        /** Chamadas a endpoints que consultam a TibiaData, por hora, por usuário. */
        int tibiadataPerHour
) {
}
