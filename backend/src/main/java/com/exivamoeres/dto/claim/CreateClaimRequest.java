package com.exivamoeres.dto.claim;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateClaimRequest(
        @NotBlank @Size(min = 2, max = 60)
        String characterName
) {
}
