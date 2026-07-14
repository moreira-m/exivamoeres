package com.exivamoeres.service;

import com.exivamoeres.dto.auth.AuthResponse;
import com.exivamoeres.dto.auth.LoginRequest;
import com.exivamoeres.dto.auth.RegisterRequest;

/** Autenticação por email/senha e contas anônimas (OAuth fica no pacote security). */
public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    /**
     * Cria um usuário anônimo e já devolve tokens — permite usar o site sem
     * cadastro. A promoção de conta anônima para registrada está prevista
     * para a sessão 2 (ver docs/proxima-sessao.md).
     */
    AuthResponse loginAnonymous(String displayName);

    AuthResponse refresh(String refreshToken);

    void logout(String refreshToken);
}
