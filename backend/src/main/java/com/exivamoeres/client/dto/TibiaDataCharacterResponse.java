package com.exivamoeres.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

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
    public record CharacterData(String name, String world, String comment) {
    }

    public boolean hasCharacter() {
        return character != null
                && character.character() != null
                && character.character().name() != null
                && !character.character().name().isBlank();
    }
}
