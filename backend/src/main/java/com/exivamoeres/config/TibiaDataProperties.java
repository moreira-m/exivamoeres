package com.exivamoeres.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.tibiadata")
public record TibiaDataProperties(String baseUrl) {
}
