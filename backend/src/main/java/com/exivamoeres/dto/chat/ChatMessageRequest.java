package com.exivamoeres.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChatMessageRequest(
        @NotNull
        Long characterId,

        @NotBlank @Size(max = 1000)
        String content
) {
}
