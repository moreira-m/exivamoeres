package com.exivamoeres.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    /** WebClient dedicado à TibiaData API (chamadas não bloqueantes no job). */
    @Bean
    public WebClient tibiaDataWebClient(TibiaDataProperties properties) {
        return WebClient.builder()
                .baseUrl(properties.baseUrl())
                .build();
    }
}
