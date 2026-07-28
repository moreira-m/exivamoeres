package com.exivamoeres.dto.list;

import com.exivamoeres.domain.ListMembership;
import com.exivamoeres.domain.TeamSlot;
import com.exivamoeres.domain.Vocation;

/**
 * Uma vaga da composição, com quem a ocupa. É o que permite a tela dizer
 * "faltam 1 EK e 1 ED" em vez de só "0/5".
 *
 * <p>{@code vocation} nula = vaga livre. {@code characterName} nulo = vaga aberta —
 * a dupla (vocação exigida, ninguém dentro) é exatamente o que falta no time.</p>
 */
public record TeamSlotResponse(
        Long id,
        int position,
        Vocation vocation,
        Long characterId,
        String characterName,
        /** Vocação de quem está na vaga (útil quando a vaga é livre). */
        Vocation characterVocation
) {
    public static TeamSlotResponse open(TeamSlot slot) {
        return new TeamSlotResponse(slot.getId(), slot.getPosition(), slot.getVocation(),
                null, null, null);
    }

    public static TeamSlotResponse occupied(TeamSlot slot, ListMembership membership) {
        return new TeamSlotResponse(slot.getId(), slot.getPosition(), slot.getVocation(),
                membership.getCharacter().getId(),
                membership.getCharacter().getName(),
                Vocation.fromTibiaData(membership.getCharacter().getVocation()));
    }
}
