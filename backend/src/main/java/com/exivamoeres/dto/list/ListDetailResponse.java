package com.exivamoeres.dto.list;

import com.exivamoeres.domain.HuntingList;

import java.util.List;

public record ListDetailResponse(
        ListSummaryResponse summary,
        Long ownerId,
        /**
         * Contato do dono (Discord, tag no jogo) — **nulo para quem não tem
         * direito de ver**: sai apenas para o dono e para membros ativos e
         * APROVADOS.
         *
         * O `GET /api/lists/{id}` é público, então quem decide é o servidor a
         * partir de quem está pedindo — nunca o cliente escondendo na tela.
         */
        String contact,
        List<MembershipResponse> members
) {
    public static ListDetailResponse from(HuntingList list, long memberCount, int maxMembers,
                                          List<MembershipResponse> members, boolean showContact) {
        return new ListDetailResponse(
                ListSummaryResponse.from(list, memberCount, maxMembers),
                list.getOwner().getId(),
                showContact ? list.getContact() : null,
                members);
    }
}
