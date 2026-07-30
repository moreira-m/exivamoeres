package com.exivamoeres.dto.list;

import com.exivamoeres.domain.HuntingList;
import com.exivamoeres.domain.ListMembership;
import com.exivamoeres.domain.MembershipStatus;
import com.exivamoeres.domain.TeamStatus;
import com.exivamoeres.domain.Vocation;

import java.time.Instant;

/**
 * Um pedido de entrada <b>do ponto de vista de quem pediu</b> — o lado que não
 * existia: `GET /api/lists/mine` só devolve time de dono ou de membro aprovado,
 * então um `PENDING` sumia da vista da pessoa.
 *
 * <p>Traz o suficiente para a tela ser útil sem uma segunda requisição por
 * pedido: identidade do time (criatura, world, título), o requisito atual
 * (`minimumLevel`), qual personagem foi usado e, quando dá para saber com dado
 * local, o {@link JoinRequestIssue} que explica por que o pedido provavelmente
 * não será aprovado.</p>
 *
 * <p><b>Não traz o `contact` do dono</b> — pedir para entrar não é participar
 * (regra do P2), e este DTO é justamente o de quem ainda não entrou.</p>
 */
public record MyJoinRequestResponse(
        /** Id da membership — é o que cancela o pedido. */
        Long id,
        Long listId,
        String listName,
        String world,
        String targetCreatureName,
        String targetCreatureImageUrl,
        TeamStatus teamStatus,
        /** Level mínimo exigido pelo time **agora** (pode ter mudado depois do pedido). */
        Integer minimumLevel,
        Long characterId,
        String characterName,
        Integer characterLevel,
        /**
         * Vocação **local** (sincronizada) do personagem do pedido. Vem para a tela
         * poder dizer *qual* vocação ficou sem vaga — "não há vaga para Druid" é
         * acionável; "sua vocação não cabe" faz a pessoa ir conferir.
         */
        Vocation characterVocation,
        MembershipStatus status,
        /** Nulo quando não há problema aparente — ver {@link JoinRequestIssue}. */
        JoinRequestIssue issue,
        Instant requestedAt
) {
    public static MyJoinRequestResponse from(ListMembership membership, JoinRequestIssue issue) {
        HuntingList list = membership.getList();
        return new MyJoinRequestResponse(
                membership.getId(),
                list.getId(),
                list.getName(),
                list.getWorld(),
                list.getTargetCreature().getName(),
                list.getTargetCreature().getImageUrl(),
                list.getStatus(),
                list.getMinimumLevel(),
                membership.getCharacter().getId(),
                membership.getCharacter().getName(),
                membership.getCharacter().getLevel(),
                Vocation.fromTibiaData(membership.getCharacter().getVocation()),
                membership.getStatus(),
                issue,
                membership.getJoinedAt());
    }
}
