package com.exivamoeres.service.impl;

import com.exivamoeres.client.TibiaCharacterSnapshot;
import com.exivamoeres.client.TibiaDataClient;
import com.exivamoeres.config.CacheConfig;
import com.exivamoeres.domain.exception.ExternalServiceException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Busca o snapshot de um personagem na TibiaData com cache (TTL em
 * app.cache.character-eligibility-ttl). Componente isolado porque
 * @Cacheable só funciona através do proxy do Spring — chamar de dentro da
 * própria classe não intercepta a chamada.
 */
@Component
public class CachedCharacterLookup {

    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(20);

    private final TibiaDataClient tibiaDataClient;

    public CachedCharacterLookup(TibiaDataClient tibiaDataClient) {
        this.tibiaDataClient = tibiaDataClient;
    }

    @Cacheable(cacheNames = CacheConfig.CHARACTER_ELIGIBILITY_CACHE, key = "#characterName.toLowerCase()")
    public TibiaCharacterSnapshot fetch(String characterName) {
        TibiaCharacterSnapshot snapshot = tibiaDataClient.fetchCharacter(characterName).block(FETCH_TIMEOUT);
        if (snapshot == null) {
            throw new ExternalServiceException("TibiaData não respondeu");
        }
        return snapshot;
    }
}
