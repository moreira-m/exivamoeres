package com.exivamoeres.service.impl;

import com.exivamoeres.client.TibiaDataClient;
import com.exivamoeres.config.CacheConfig;
import com.exivamoeres.domain.exception.ExternalServiceException;
import com.exivamoeres.service.WorldService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class WorldServiceImpl implements WorldService {

    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(20);

    private final TibiaDataClient tibiaDataClient;

    public WorldServiceImpl(TibiaDataClient tibiaDataClient) {
        this.tibiaDataClient = tibiaDataClient;
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.WORLDS_CACHE)
    public List<String> listWorlds() {
        List<String> worlds = tibiaDataClient.fetchWorlds().block(FETCH_TIMEOUT);
        if (worlds == null) {
            throw new ExternalServiceException("TibiaData não respondeu");
        }
        return worlds;
    }
}
