package com.exivamoeres.dto.list;

import com.exivamoeres.domain.ListMembership;
import com.exivamoeres.domain.MembershipStatus;

import java.time.Instant;

public record MembershipResponse(
        Long id,
        Long userId,
        Long characterId,
        String characterName,
        String vocation,
        Integer level,
        MembershipStatus status,
        boolean active,
        Instant joinedAt
) {
    public static MembershipResponse from(ListMembership membership) {
        return new MembershipResponse(
                membership.getId(),
                membership.getUser().getId(),
                membership.getCharacter().getId(),
                membership.getCharacter().getName(),
                membership.getCharacter().getVocation(),
                membership.getCharacter().getLevel(),
                membership.getStatus(),
                membership.isActive(),
                membership.getJoinedAt());
    }
}
