package com.exivamoeres.dto.auth;

import jakarta.validation.constraints.Size;

public record AnonymousLoginRequest(
        @Size(max = 100)
        String displayName
) {
}
