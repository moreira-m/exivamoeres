package com.exivamoeres.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * TTL do cache de elegibilidade de personagem (world + status de conta) e de
 * worlds válidos. Curto o suficiente para não deixar um char que virou
 * Premium bloqueado por muito tempo, longo o suficiente para não martelar a
 * TibiaData a cada ação do usuário.
 */
@ConfigurationProperties(prefix = "app.cache")
public record CacheProperties(
        Duration characterEligibilityTtl,
        Duration worldsTtl
) {
}
