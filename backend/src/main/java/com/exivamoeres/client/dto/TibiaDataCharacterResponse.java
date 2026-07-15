package com.exivamoeres.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Mapeia só o que interessa do JSON da TibiaData v4:
 * { "character": { "character": { "name", "world", "comment", ... } } }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TibiaDataCharacterResponse(CharacterWrapper character) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CharacterWrapper(CharacterData character) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CharacterData(
            String name,
            String world,
            String comment,
            @JsonProperty("account_status") String accountStatus,
            String vocation
    ) {
    }

    public boolean hasCharacter() {
        return character != null
                && character.character() != null
                && character.character().name() != null
                && !character.character().name().isBlank();
    }
}
