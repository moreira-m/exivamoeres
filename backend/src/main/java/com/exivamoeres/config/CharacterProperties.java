package com.exivamoeres.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Config do refresh periódico de level. Pensado para custo mínimo de chamadas
 * à TibiaData: só re-sincroniza personagens em time ativo (ver query no
 * repositório), e só quando o retrato local está mais velho que
 * {@code levelStaleness}. O lote e a pausa entre chamadas evitam rajadas.
 */
@ConfigurationProperties(prefix = "app.character")
public record CharacterProperties(
        Duration levelRefreshInterval,
        Duration levelStaleness,
        int levelRefreshBatchSize,
        Duration levelRefreshSpacing
) {
}
