package com.exivamoeres.security.oauth;

import com.exivamoeres.domain.User;
import com.exivamoeres.repository.UserRepository;
import com.exivamoeres.security.JwtService;
import com.exivamoeres.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * Fecha o ciclo do login social: emite os tokens da aplicação e devolve o
 * usuário ao frontend. Os tokens vão no fragmento (#) da URL — o fragmento
 * não é enviado a servidores nem aparece em logs de acesso.
 */
@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;
    private final String redirectUrl;

    public OAuth2LoginSuccessHandler(JwtService jwtService,
                                     RefreshTokenService refreshTokenService,
                                     UserRepository userRepository,
                                     @Value("${app.oauth2.redirect-url}") String redirectUrl) {
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.userRepository = userRepository;
        this.redirectUrl = redirectUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();
        Long userId = extractAppUserId(oauthUser);
        User user = userRepository.findById(userId).orElseThrow();

        String target = UriComponentsBuilder.fromUriString(redirectUrl)
                .fragment("access_token=" + jwtService.generateAccessToken(user)
                        + "&refresh_token=" + refreshTokenService.issue(user))
                .build()
                .toUriString();
        getRedirectStrategy().sendRedirect(request, response, target);
    }

    /**
     * Lê o id do User local injetado por CustomOAuth2UserService/CustomOidcUserService.
     * Não assume um tipo Java específico: dependendo do caminho (OAuth2 comum
     * vs. OIDC) e de serialização intermediária, o valor pode chegar como
     * Number ou String — nunca deve depender do formato de atributo de um
     * provider específico.
     *
     * Falha explícita (em vez de NPE) se o atributo estiver ausente: isso só
     * acontece se um novo provider for registrado sem também cadastrar seu
     * serviço em SecurityConfig (.userService/.oidcUserService).
     */
    private Long extractAppUserId(OAuth2User oauthUser) {
        Object rawId = oauthUser.getAttributes().get(OAuthLocalUserResolver.APP_USER_ID_ATTRIBUTE);
        if (rawId instanceof Number number) {
            return number.longValue();
        }
        if (rawId instanceof String text) {
            return Long.parseLong(text);
        }
        throw new IllegalStateException(
                "Atributo '" + OAuthLocalUserResolver.APP_USER_ID_ATTRIBUTE
                        + "' ausente ou em formato inesperado no principal OAuth2/OIDC — "
                        + "verifique se o provider está registrado em userService/oidcUserService "
                        + "no SecurityConfig.");
    }
}
