package com.exivamoeres.service.impl;

import com.exivamoeres.config.JwtProperties;
import com.exivamoeres.domain.RefreshToken;
import com.exivamoeres.domain.User;
import com.exivamoeres.domain.exception.BusinessRuleException;
import com.exivamoeres.repository.RefreshTokenRepository;
import com.exivamoeres.service.RefreshTokenService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

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
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setToken(randomToken());
        token.setExpiresAt(Instant.now().plus(ttl));
        repository.save(token);
        return token.getToken();
    }

    @Override
    @Transactional
    public RotationResult rotate(String rawToken) {
        RefreshToken current = repository.findByToken(rawToken)
                .filter(RefreshToken::isUsable)
                .orElseThrow(() -> new BusinessRuleException("Refresh token inválido ou expirado"));
        current.setRevoked(true);
        return new RotationResult(current.getUser(), issue(current.getUser()));
    }

    @Override
    @Transactional
    public void revoke(String rawToken) {
        repository.findByToken(rawToken).ifPresent(token -> token.setRevoked(true));
    }

    private String randomToken() {
        // 48 bytes aleatórios ~ 64 chars em base64url: inviável de adivinhar.
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
