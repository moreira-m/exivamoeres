package com.exivamoeres.dto.list;

import jakarta.validation.constraints.NotNull;

public record JoinListRequest(
        @NotNull
        Long characterId
) {
}
