package com.exivamoeres.dto.soulcore;

import com.exivamoeres.domain.ListSoulcore;
import com.exivamoeres.domain.SoulcoreStatus;

import java.time.Instant;

public record ListSoulcoreResponse(
        Long id,
        Long creatureId,
        String creatureName,
        SoulcoreStatus status,
        Long obtainedByCharacterId,
        String obtainedByCharacterName,
        Instant createdAt,
        Instant updatedAt
) {
    public static ListSoulcoreResponse from(ListSoulcore soulcore) {
        return new ListSoulcoreResponse(
                soulcore.getId(),
                soulcore.getCreature().getId(),
                soulcore.getCreature().getName(),
                soulcore.getStatus(),
                soulcore.getObtainedBy() != null ? soulcore.getObtainedBy().getId() : null,
                soulcore.getObtainedBy() != null ? soulcore.getObtainedBy().getName() : null,
                soulcore.getCreatedAt(),
                soulcore.getUpdatedAt());
    }
}
