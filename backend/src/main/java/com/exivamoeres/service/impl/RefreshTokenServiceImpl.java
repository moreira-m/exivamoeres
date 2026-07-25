package com.exivamoeres.service.impl;

import com.exivamoeres.config.JwtProperties;
import com.exivamoeres.domain.RefreshToken;
import com.exivamoeres.domain.User;
import com.exivamoeres.domain.exception.BusinessRuleException;
import com.exivamoeres.repository.RefreshTokenRepository;
import com.exivamoeres.service.RefreshTokenService;
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

    @Override
    @Transactional
    public RotationResult rotate(String rawToken) {
        RefreshToken current = repository.findByTokenHash(sha256(rawToken))
                .filter(RefreshToken::isUsable)
                .orElseThrow(() -> new BusinessRuleException("Refresh token inválido ou expirado"));
        current.setRevoked(true);
        return new RotationResult(current.getUser(), issue(current.getUser()));
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
