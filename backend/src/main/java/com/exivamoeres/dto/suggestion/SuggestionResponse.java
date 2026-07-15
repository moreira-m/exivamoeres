package com.exivamoeres.dto.suggestion;

import com.exivamoeres.domain.SoulcoreSuggestion;

import java.time.Instant;

public record SuggestionResponse(
        Long id,
        Long creatureId,
        String creatureName,
        Integer difficulty,
        String reason,
        Instant createdAt
) {
    public static SuggestionResponse from(SoulcoreSuggestion suggestion) {
        return new SuggestionResponse(
                suggestion.getId(),
                suggestion.getCreature().getId(),
                suggestion.getCreature().getName(),
                suggestion.getCreature().getDifficulty(),
                suggestion.getReason(),
                suggestion.getCreatedAt());
    }
}
