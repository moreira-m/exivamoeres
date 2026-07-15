package com.exivamoeres.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {

    public static final String CHARACTER_ELIGIBILITY_CACHE = "characterEligibility";
    public static final String WORLDS_CACHE = "worlds";

    @Bean
    public CacheManager cacheManager(CacheProperties properties) {
        var eligibilityCache = buildCache(properties.characterEligibilityTtl(), 500);
        var worldsCache = buildCache(properties.worldsTtl(), 1);

        var manager = new CaffeineCacheManager();
        manager.registerCustomCache(CHARACTER_ELIGIBILITY_CACHE, eligibilityCache.build());
        manager.registerCustomCache(WORLDS_CACHE, worldsCache.build());
        return manager;
    }

    private Caffeine<Object, Object> buildCache(java.time.Duration ttl, int maxSize) {
        return Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maxSize);
    }
}
