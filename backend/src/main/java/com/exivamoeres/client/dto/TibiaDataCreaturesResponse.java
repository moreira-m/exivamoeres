package com.exivamoeres.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Mapeia a LISTA completa do Bestiary da TibiaData v4:
 * { "creatures": { "creature_list": [ { "name", "race", "image_url" }, ... ] } }
 * (não confundir com o /v4/creature/{race}, que é o detalhe de uma criatura).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TibiaDataCreaturesResponse(CreaturesWrapper creatures) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreaturesWrapper(@JsonProperty("creature_list") List<CreatureEntry> creatureList) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreatureEntry(String name, String race, @JsonProperty("image_url") String imageUrl) {
    }

    public List<CreatureEntry> entries() {
        return creatures == null || creatures.creatureList() == null
                ? List.of()
                : creatures.creatureList();
    }
}
