package com.exivamoeres.service;

import com.exivamoeres.domain.User;

/** Ciclo de vida dos refresh tokens opacos persistidos em banco. */
public interface RefreshTokenService {

    /** Emite um novo refresh token para o usuário e o persiste. */
    String issue(User user);

    /**
     * Valida e rotaciona: o token usado é revogado e um novo é emitido
     * (rotação limita a janela de uso de um token vazado).
     *
     * @return o usuário dono do token
     * @throws com.exivamoeres.domain.exception.BusinessRuleException se inválido/expirado
     */
    RotationResult rotate(String token);

    void revoke(String token);

    record RotationResult(User user, String newRefreshToken) {
    }
}
