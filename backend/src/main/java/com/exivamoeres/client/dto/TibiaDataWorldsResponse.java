package com.exivamoeres.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Mapeia { "worlds": { "regular_worlds": [ { "name": ... }, ... ] } } da TibiaData v4. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TibiaDataWorldsResponse(WorldsWrapper worlds) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WorldsWrapper(@JsonProperty("regular_worlds") List<WorldEntry> regularWorlds) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WorldEntry(String name) {
    }

    public List<String> names() {
        if (worlds == null || worlds.regularWorlds() == null) {
            return List.of();
        }
        return worlds.regularWorlds().stream().map(WorldEntry::name).toList();
    }
}
