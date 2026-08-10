package com.exivamoeres.service.impl;

import com.exivamoeres.config.JwtProperties;
import com.exivamoeres.domain.RefreshToken;
import com.exivamoeres.domain.User;
import com.exivamoeres.domain.exception.InvalidCredentialsException;
import com.exivamoeres.repository.RefreshTokenRepository;
import com.exivamoeres.service.RefreshTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
@Slf4j
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final Duration ttl;

    public RefreshTokenServiceImpl(RefreshTokenRepository repository, JwtProperties properties) {
        this.repository = repository;
        this.ttl = Duration.ofDays(properties.refreshTokenDays());
    }

    @Override
    @Transactional
    public String issue(User user) {
        // O valor cru só existe aqui e na resposta HTTP: o banco recebe o hash.
        String rawToken = randomToken();
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(sha256(rawToken));
        token.setExpiresAt(Instant.now().plus(ttl));
        repository.save(token);
        return rawToken;
    }

    /** Mensagem única para os três motivos de recusa — a diferença vai só para o log. */
    private static final String RECUSADO = "Sessão expirada. Entre novamente.";

    @Override
    @Transactional
    public RotationResult rotate(String rawToken) {
        RefreshToken current = repository.findByTokenHash(sha256(rawToken))
                .orElseThrow(() -> {
                    // Token que nunca existiu (ou de outro ambiente): sem userId para logar.
                    log.warn("auth.refresh_rejected reason=unknown_token");
                    return new InvalidCredentialsException(RECUSADO);
                });

        if (!current.isUsable()) {
            // Aqui a distinção importa, e é o motivo de a consulta ter sido separada do
            // filtro: **revogado** é o sinal de token reaproveitado (S7, detecção de
            // reuso), e **expirado** é rotina. Misturados num `filter`, os dois viravam
            // a mesma linha de log e a diferença se perdia.
            if (current.isRevoked()) {
                derrubarSessoesDe(current.getUser().getId());
            } else {
                log.warn("auth.refresh_rejected reason=expired userId={}", current.getUser().getId());
            }
            throw new InvalidCredentialsException(RECUSADO);
        }

        current.setRevoked(true);
        return new RotationResult(current.getUser(), issue(current.getUser()));
    }

    /**
     * Um token já rotacionado voltou. Só há duas explicações, e nenhuma delas permite
     * saber <b>quem</b> está apresentando: ou a cópia vazou, ou o dono ficou com uma
     * sessão velha. Como não dá para distinguir o dono do ladrão, cai a cadeia inteira —
     * quem for o dono entra de novo com a credencial dele; quem copiou fica sem nada.
     *
     * O logout de todos os dispositivos é o preço, e é barato perto da alternativa:
     * antes, o sinal clássico de token roubado só virava uma linha de log, e a sessão
     * que o ladrão já tinha rotacionado continuava válida por 14 dias.
     */
    private void derrubarSessoesDe(Long userId) {
        int derrubadas = repository.revokeAllForUser(userId);
        log.warn("auth.refresh.reuse_detected userId={} revokedSessions={}", userId, derrubadas);
    }

    @Override
    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHash(sha256(rawToken)).ifPresent(token -> token.setRevoked(true));
    }

    private String randomToken() {
        // 48 bytes aleatórios ~ 64 chars em base64url: inviável de adivinhar.
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256 basta aqui: o token já é aleatório de 48 bytes, então não há
     * dicionário nem força bruta a atrasar — BCrypt só custaria latência em
     * todo refresh. Sem salt de propósito: a busca é feita pelo próprio hash.
     */
    private String sha256(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 é obrigatório em qualquer JVM", e);
        }
    }
}
