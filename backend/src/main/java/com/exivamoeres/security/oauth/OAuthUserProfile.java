package com.exivamoeres.security.oauth;

import com.exivamoeres.domain.AuthProvider;

/** Dados mínimos normalizados de um perfil vindo de qualquer provider OAuth. */
public record OAuthUserProfile(
        AuthProvider provider,
        String providerId,
        String email,
        String displayName
) {
}
