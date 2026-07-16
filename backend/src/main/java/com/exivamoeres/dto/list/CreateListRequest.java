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
        Long pricePerSlot
) {
}
