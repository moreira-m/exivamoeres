package com.exivamoeres.service.impl;

import com.exivamoeres.domain.AuthProvider;
import com.exivamoeres.domain.User;
import com.exivamoeres.domain.exception.BusinessRuleException;
import com.exivamoeres.dto.auth.AuthResponse;
import com.exivamoeres.dto.auth.LoginRequest;
import com.exivamoeres.dto.auth.RegisterRequest;
import com.exivamoeres.dto.auth.UserResponse;
import com.exivamoeres.repository.UserRepository;
import com.exivamoeres.security.JwtService;
import com.exivamoeres.service.AuthService;
import com.exivamoeres.service.RefreshTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JwtService jwtService,
                           RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new BusinessRuleException("Já existe uma conta com este email");
        }
        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName());
        user.setAuthProvider(AuthProvider.LOCAL);
        userRepository.save(user);
        return buildAuthResponse(user);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Mensagem idêntica para email inexistente e senha errada — não
        // revelar quais emails têm conta.
        User user = userRepository.findByEmailIgnoreCase(request.email())
                .filter(u -> u.getPasswordHash() != null)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BusinessRuleException("Email ou senha incorretos"));
        return buildAuthResponse(user);
    }

    @Override
    @Transactional
    public AuthResponse loginAnonymous(String displayName) {
        User user = new User();
        user.setDisplayName(displayName != null && !displayName.isBlank()
                ? displayName.trim()
                : "Anônimo");
        user.setAuthProvider(AuthProvider.ANONYMOUS);
        userRepository.save(user);
        return buildAuthResponse(user);
    }

    @Override
    @Transactional
    public AuthResponse refresh(String refreshToken) {
        RefreshTokenService.RotationResult result = refreshTokenService.rotate(refreshToken);
        return new AuthResponse(
                jwtService.generateAccessToken(result.user()),
                result.newRefreshToken(),
                UserResponse.from(result.user()));
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private AuthResponse buildAuthResponse(User user) {
        return new AuthResponse(
                jwtService.generateAccessToken(user),
                refreshTokenService.issue(user),
                UserResponse.from(user));
    }
}
