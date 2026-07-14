package com.exivamoeres.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Parâmetros de emissão de tokens (ver bloco app.jwt no application.yml). */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        int accessTokenMinutes,
        int refreshTokenDays
) {
}
