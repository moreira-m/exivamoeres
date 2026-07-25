package com.exivamoeres.integration;

import com.exivamoeres.domain.RefreshToken;
import com.exivamoeres.domain.exception.BusinessRuleException;
import com.exivamoeres.dto.auth.AuthResponse;
import com.exivamoeres.repository.RefreshTokenRepository;
import com.exivamoeres.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guarda do refresh token: o banco só vê o hash, e o ciclo
 * rotação → reuso → revogação → expiração se comporta como esperado.
 *
 * As sessões são criadas por conta anônima de propósito: é o caminho mais
 * barato de conseguir um par de tokens e não passa pelo rate limit de login.
 */
@AutoConfigureMockMvc
class RefreshTokenIntegrationTest extends IntegrationTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired RefreshTokenRepository refreshTokenRepository;

    @Test
    void emissaoGuardaApenasOHashDoToken() {
        String rawToken = novaSessao().refreshToken();

        assertThat(refreshTokenRepository.findByTokenHash(sha256(rawToken)))
                .as("o token deve ser localizável pelo hash")
                .isPresent();
        assertThat(refreshTokenRepository.findAll())
                .extracting(RefreshToken::getTokenHash)
                .as("o valor cru não pode aparecer em nenhuma linha")
                .doesNotContain(rawToken);
    }

    @Test
    void rotacaoDevolveTokenNovoQueContinuaValendo() {
        String rawToken = novaSessao().refreshToken();

        String rotacionado = authService.refresh(rawToken).refreshToken();

        assertThat(rotacionado).isNotEqualTo(rawToken);
        assertThat(authService.refresh(rotacionado).refreshToken()).isNotBlank();
    }

    @Test
    void tokenJaRotacionadoNaoServeDeNovo() {
        String rawToken = novaSessao().refreshToken();
        authService.refresh(rawToken);

        assertThatThrownBy(() -> authService.refresh(rawToken))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("inválido ou expirado");
    }

    @Test
    void logoutRevogaOTokenParaRefreshesFuturos() {
        String rawToken = novaSessao().refreshToken();

        authService.logout(rawToken);

        assertThatThrownBy(() -> authService.refresh(rawToken))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("inválido ou expirado");
    }

    @Test
    void tokenExpiradoNaoRotaciona() {
        String rawToken = novaSessao().refreshToken();
        RefreshToken persistido = refreshTokenRepository.findByTokenHash(sha256(rawToken)).orElseThrow();
        persistido.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        refreshTokenRepository.save(persistido);

        assertThatThrownBy(() -> authService.refresh(rawToken))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("inválido ou expirado");
    }

    @Test
    void refreshComTokenDesconhecidoRetorna422() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"token-que-nunca-foi-emitido"}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    private AuthResponse novaSessao() {
        return authService.loginAnonymous("Visitante");
    }

    /** Cálculo independente do hash: o teste não usa o código de produção para conferir o produção. */
    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
