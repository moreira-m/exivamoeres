package com.exivamoeres.dto.list;

import com.exivamoeres.domain.HuntingList;
import com.exivamoeres.domain.JoinPolicy;
import com.exivamoeres.domain.TeamStatus;

import java.time.Instant;
import java.util.List;

public record ListSummaryResponse(
        Long id,
        String name,
        String world,
        String shareCode,
        Long targetCreatureId,
        String targetCreatureName,
        String targetCreatureImageUrl,
        JoinPolicy joinPolicy,
        TeamStatus status,
        Instant expiresAt,
        Integer minimumLevel,
        Long pricePerSlot,
        /** Texto livre do dono (opcional). Público: ajuda a escolher o time. */
        String description,
        /**
         * Horário da caçada em texto livre (opcional). Público de propósito — é
         * a informação que faz alguém decidir se o time serve para ele.
         *
         * ⚠️ O `contact` NÃO mora aqui: este DTO sai na busca pública, e contato
         * é dado pessoal (ver ListDetailResponse).
         */
        String huntSchedule,
        /** Anúncio em destaque: verdadeiro quando o dono é premium. */
        boolean featured,
        int memberCount,
        int maxMembers,
        boolean hasOpenSlots,
        /**
         * Composição por vocação — **vazia** quando o time não configurou nenhuma
         * (aceita qualquer vocação). Vem também na busca: é o que permite ao card
         * dizer "faltam 1 EK e 1 ED".
         */
        List<TeamSlotResponse> slots,
        Instant createdAt
) {
    public static ListSummaryResponse from(HuntingList list, long memberCount, int maxMembers) {
        return from(list, memberCount, maxMembers, List.of());
    }

    public static ListSummaryResponse from(HuntingList list, long memberCount, int maxMembers,
                                           List<TeamSlotResponse> slots) {
        return new ListSummaryResponse(
                list.getId(),
                list.getName(),
                list.getWorld(),
                list.getShareCode(),
                list.getTargetCreature().getId(),
                list.getTargetCreature().getName(),
                list.getTargetCreature().getImageUrl(),
                list.getJoinPolicy(),
                list.getStatus(),
                list.getExpiresAt(),
                list.getMinimumLevel(),
                list.getPricePerSlot(),
                list.getDescription(),
                list.getHuntSchedule(),
                list.getOwner().isPremium(),
                (int) memberCount,
                maxMembers,
                memberCount < maxMembers,
                slots,
                list.getCreatedAt());
    }
}
