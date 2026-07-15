package com.exivamoeres.security.oauth;

import com.exivamoeres.domain.AuthProvider;
import com.exivamoeres.domain.User;
import com.exivamoeres.repository.UserRepository;
import com.exivamoeres.security.JwtService;
import com.exivamoeres.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regressão do bug em produção: login com Google lançava
 * "NullPointerException: Cannot invoke Number.longValue() because the return
 * value of Map.get(Object) is null" na extração do app_user_id. Testa a
 * extração diretamente com os DOIS formatos reais de principal que o Spring
 * Security produz — DefaultOAuth2User (Discord) e DefaultOidcUser (Google) —
 * sem assumir o formato de nenhum provider específico.
 */
@ExtendWith(MockitoExtension.class)
class OAuth2LoginSuccessHandlerTest {

    @Mock JwtService jwtService;
    @Mock RefreshTokenService refreshTokenService;
    @Mock UserRepository userRepository;
    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;

    @InjectMocks OAuth2LoginSuccessHandler handler;

    @Test
    void processaLoginComPrincipalNoFormatoDoGoogleOidc() throws IOException {
        ReflectionTestUtils.setField(handler, "redirectUrl", "http://localhost:5173/oauth/callback");
        RedirectStrategy redirectStrategy = setSpyRedirectStrategy();

        Long appUserId = 42L;
        OidcUser googlePrincipal = googleShapedPrincipal(appUserId);
        User user = existingUser(appUserId, AuthProvider.GOOGLE);
        when(userRepository.findById(appUserId)).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(user)).thenReturn("access-token");
        when(refreshTokenService.issue(user)).thenReturn("refresh-token");

        handler.onAuthenticationSuccess(request, response, authenticationOf(googlePrincipal));

        verify(redirectStrategy).sendRedirect(eq(request), eq(response), any());
    }

    @Test
    void processaLoginComPrincipalNoFormatoDoDiscordOAuth2() throws IOException {
        ReflectionTestUtils.setField(handler, "redirectUrl", "http://localhost:5173/oauth/callback");
        RedirectStrategy redirectStrategy = setSpyRedirectStrategy();

        Long appUserId = 7L;
        OAuth2User discordPrincipal = discordShapedPrincipal(appUserId);
        User user = existingUser(appUserId, AuthProvider.DISCORD);
        when(userRepository.findById(appUserId)).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(user)).thenReturn("access-token");
        when(refreshTokenService.issue(user)).thenReturn("refresh-token");

        handler.onAuthenticationSuccess(request, response, authenticationOf(discordPrincipal));

        verify(redirectStrategy).sendRedirect(eq(request), eq(response), any());
    }

    @Test
    void falhaComMensagemClaraSeOAtributoNuncaFoiInjetado() {
        // Reproduz a causa raiz do bug (provider registrado sem userService/
        // oidcUserService): sem app_user_id nos atributos, falha explicitamente
        // em vez do NullPointerException original.
        OAuth2User principalSemAtributo = new DefaultOAuth2User(
                List.of(), Map.of("id", "123", "username", "alguem"), "id");

        assertThatThrownBy(() ->
                handler.onAuthenticationSuccess(request, response, authenticationOf(principalSemAtributo)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(OAuthLocalUserResolver.APP_USER_ID_ATTRIBUTE);
    }

    private OidcUser googleShapedPrincipal(Long appUserId) {
        Instant now = Instant.now();
        Map<String, Object> claims = new LinkedHashMap<>(Map.of(
                "sub", "108913749081234567890",
                "email", "sir.exiva@gmail.com",
                "name", "Sir Exiva"));
        claims.put(OAuthLocalUserResolver.APP_USER_ID_ATTRIBUTE, appUserId);
        OidcIdToken idToken = new OidcIdToken("token", now, now.plusSeconds(3600), claims);
        return new DefaultOidcUser(List.of(), idToken, new OidcUserInfo(claims));
    }

    private OAuth2User discordShapedPrincipal(Long appUserId) {
        Map<String, Object> attributes = new LinkedHashMap<>(Map.of(
                "id", "823456789012345678",
                "username", "sirexiva",
                "global_name", "Sir Exiva"));
        attributes.put(OAuthLocalUserResolver.APP_USER_ID_ATTRIBUTE, appUserId);
        return new DefaultOAuth2User(List.of(), attributes, "id");
    }

    private User existingUser(Long id, AuthProvider provider) {
        User user = new User();
        user.setId(id);
        user.setAuthProvider(provider);
        user.setDisplayName("Sir Exiva");
        return user;
    }

    private Authentication authenticationOf(OAuth2User principal) {
        return new TestingAuthenticationToken(principal, null);
    }

    /** SimpleUrlAuthenticationSuccessHandler resolve a RedirectStrategy lazily; espiamos via reflection. */
    private RedirectStrategy setSpyRedirectStrategy() {
        RedirectStrategy spy = org.mockito.Mockito.mock(RedirectStrategy.class);
        handler.setRedirectStrategy(spy);
        return spy;
    }
}
