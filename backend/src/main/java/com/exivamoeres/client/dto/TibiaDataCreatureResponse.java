package com.exivamoeres.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Mapeia { "creature": { "name", "race", "image_url", ... } } da TibiaData v4. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TibiaDataCreatureResponse(CreatureData creature) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreatureData(String name, String race, @JsonProperty("image_url") String imageUrl) {
    }

    public boolean hasCreature() {
        return creature != null && creature.name() != null && !creature.name().isBlank();
    }
}
