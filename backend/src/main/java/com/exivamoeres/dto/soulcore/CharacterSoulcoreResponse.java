package com.exivamoeres.dto.soulcore;

import com.exivamoeres.domain.CharacterSoulcore;

import java.time.Instant;

public record CharacterSoulcoreResponse(
        Long creatureId,
        String creatureName,
        Instant unlockedAt
) {
    public static CharacterSoulcoreResponse from(CharacterSoulcore soulcore) {
        return new CharacterSoulcoreResponse(
                soulcore.getCreature().getId(),
                soulcore.getCreature().getName(),
                soulcore.getUnlockedAt());
    }
}
