package com.exivamoeres.dto.character;

import com.exivamoeres.domain.Character;

public record CharacterSummaryResponse(
        Long id,
        String name,
        String world,
        String vocation,
        Integer level
) {
    public static CharacterSummaryResponse from(Character character) {
        return new CharacterSummaryResponse(
                character.getId(), character.getName(), character.getWorld(),
                character.getVocation(), character.getLevel());
    }
}
