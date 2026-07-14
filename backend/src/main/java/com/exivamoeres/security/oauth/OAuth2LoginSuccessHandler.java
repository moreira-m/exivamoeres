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
        Long userId = ((Number) oauthUser.getAttributes()
                .get(CustomOAuth2UserService.APP_USER_ID_ATTRIBUTE)).longValue();
        User user = userRepository.findById(userId).orElseThrow();

        String target = UriComponentsBuilder.fromUriString(redirectUrl)
                .fragment("access_token=" + jwtService.generateAccessToken(user)
                        + "&refresh_token=" + refreshTokenService.issue(user))
                .build()
                .toUriString();
        getRedirectStrategy().sendRedirect(request, response, target);
    }
}
