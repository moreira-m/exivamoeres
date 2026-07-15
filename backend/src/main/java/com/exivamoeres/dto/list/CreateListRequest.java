package com.exivamoeres.dto.list;

import com.exivamoeres.domain.JoinPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateListRequest(
        @NotBlank @Size(min = 3, max = 100)
        String name,

        @NotBlank @Size(max = 40)
        String world,

        @NotNull
        Long targetCreatureId,

        @NotNull
        JoinPolicy joinPolicy,

        /** Personagem do criador que já entra como primeiro membro do time. */
        @NotNull
        Long characterId
) {
}
