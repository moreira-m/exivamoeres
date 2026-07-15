package com.exivamoeres.dto.list;

import com.exivamoeres.domain.HuntingList;
import com.exivamoeres.domain.JoinPolicy;

import java.time.Instant;

public record ListSummaryResponse(
        Long id,
        String name,
        String world,
        String shareCode,
        Long targetCreatureId,
        String targetCreatureName,
        String targetCreatureImageUrl,
        JoinPolicy joinPolicy,
        int memberCount,
        int maxMembers,
        boolean hasOpenSlots,
        Instant createdAt
) {
    public static ListSummaryResponse from(HuntingList list, long memberCount, int maxMembers) {
        return new ListSummaryResponse(
                list.getId(),
                list.getName(),
                list.getWorld(),
                list.getShareCode(),
                list.getTargetCreature().getId(),
                list.getTargetCreature().getName(),
                list.getTargetCreature().getImageUrl(),
                list.getJoinPolicy(),
                (int) memberCount,
                maxMembers,
                memberCount < maxMembers,
                list.getCreatedAt());
    }
}
