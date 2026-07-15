package com.exivamoeres.dto.soulcore;

import jakarta.validation.constraints.NotNull;

public record SoulcoreActionRequest(
        @NotNull
        Long characterId
) {
}
