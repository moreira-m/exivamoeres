package com.exivamoeres.dto.auth;

import com.exivamoeres.domain.AuthProvider;
import com.exivamoeres.domain.User;

public record UserResponse(
        Long id,
        String displayName,
        String email,
        AuthProvider authProvider,
        boolean anonymous
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getAuthProvider(),
                user.isAnonymous());
    }
}
