package com.exivamoeres.dto.list;

import com.exivamoeres.domain.JoinPolicy;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateListRequest(
        /** Título opcional; se vazio, assume o nome da criatura-alvo. */
        @Size(max = 100)
        String name,

        @jakarta.validation.constraints.NotBlank @Size(max = 40)
        String world,

        @NotNull
        Long targetCreatureId,

        @NotNull
        JoinPolicy joinPolicy,

        /** Personagem do criador que já entra como primeiro membro do time. */
        @NotNull
        Long characterId,

        /** Level mínimo exigido (opcional; nulo = sem restrição). */
        @Min(1)
        Integer minimumLevel,

        /** Preço informativo por vaga em gold (opcional; nulo = não informado). */
        @PositiveOrZero
        Long pricePerSlot,

        /** Estratégia, requisitos, regras do time. Texto livre opcional. */
        @Size(max = 500)
        String description,

        /** Horário da caçada em texto livre ("Seg–Sex 20h BRT"). Opcional. */
        @Size(max = 120)
        String huntSchedule,

        /**
         * Contato do dono (Discord, tag no jogo). Opcional.
         * ⚠️ Dado pessoal: só volta para o dono e para membros aprovados.
         */
        @Size(max = 120)
        String contact
) {
}
