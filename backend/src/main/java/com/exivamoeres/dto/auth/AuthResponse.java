package com.exivamoeres.dto.auth;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        UserResponse user
) {
}
