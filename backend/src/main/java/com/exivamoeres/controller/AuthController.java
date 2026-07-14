package com.exivamoeres.controller;

import com.exivamoeres.dto.auth.AnonymousLoginRequest;
import com.exivamoeres.dto.auth.AuthResponse;
import com.exivamoeres.dto.auth.LoginRequest;
import com.exivamoeres.dto.auth.RefreshTokenRequest;
import com.exivamoeres.dto.auth.RegisterRequest;
import com.exivamoeres.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** Entrada sem cadastro: cria usuário anônimo e devolve tokens. */
    @PostMapping("/anonymous")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse anonymous(@Valid @RequestBody(required = false) AnonymousLoginRequest request) {
        return authService.loginAnonymous(request != null ? request.displayName() : null);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
    }
}
