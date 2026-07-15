package com.exivamoeres.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.team")
public record TeamProperties(int maxMembers) {
}
