package com.exivamoeres.security.oauth;

import com.exivamoeres.domain.AuthProvider;
import com.exivamoeres.domain.User;
import com.exivamoeres.integration.IntegrationTestBase;
import com.exivamoeres.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regressão do bug: login com Google (provider OIDC, por causa do scope
 * "openid") pulava CustomOAuth2UserService inteiramente e nunca injetava
 * app_user_id, causando NPE no OAuth2LoginSuccessHandler. Este teste exercita
 * os dois fluxos (OAuth2 comum via Discord, OIDC via Google) com o formato de
 * atributo REAL de cada provider — que são bem diferentes entre si — e
 * confirma que ambos produzem o atributo interno corretamente.
 */
class OAuthUserEnrichmentIntegrationTest extends IntegrationTestBase {

    @Autowired CustomOAuth2UserService customOAuth2UserService;
    @Autowired CustomOidcUserService customOidcUserService;
    @Autowired UserRepository userRepository;

    @Test
    void enriquecePrincipalDoDiscordComAppUserId() {
        // Formato real do Discord (GET /users/@me) — chave de nome "id",
        // sem "sub", sem claims de OIDC.
        Map<String, Object> discordAttributes = Map.of(
                "id", "823456789012345678",
                "username", "sirexiva",
                "global_name", "Sir Exiva",
                "discriminator", "0",
                "avatar", "a1b2c3d4e5f6",
                "email", "sir.exiva@discord.example",
                "verified", true);
        OAuth2User rawPrincipal = new DefaultOAuth2User(
                List.of(), discordAttributes, "id");

        OAuth2User enriched = customOAuth2UserService.enrich(rawPrincipal, "discord", "id");

        Object appUserId = enriched.getAttributes().get(OAuthLocalUserResolver.APP_USER_ID_ATTRIBUTE);
        assertThat(appUserId).isInstanceOf(Long.class);
        // Atributos originais do provider continuam presentes.
        assertThat(enriched.getAttributes()).containsEntry("username", "sirexiva");

        User created = userRepository.findById((Long) appUserId).orElseThrow();
        assertThat(created.getAuthProvider()).isEqualTo(AuthProvider.DISCORD);
        assertThat(created.getProviderId()).isEqualTo("823456789012345678");
        assertThat(created.getDisplayName()).isEqualTo("Sir Exiva");
        assertThat(created.getEmail()).isEqualTo("sir.exiva@discord.example");
    }

    @Test
    void enriquecePrincipalDoGoogleComAppUserId() {
        // Formato real do Google (claims OIDC: id_token + userinfo) — chave de
        // nome "sub", sem "id". Bem diferente do shape do Discord.
        Instant now = Instant.now();
        Map<String, Object> googleClaims = Map.of(
                "sub", "108913749081234567890",
                "iss", "https://accounts.google.com",
                "aud", "google-client-id",
                "name", "Sir Exiva",
                "given_name", "Sir",
                "family_name", "Exiva",
                "picture", "https://lh3.googleusercontent.com/a/xyz",
                "email", "sir.exiva@gmail.com",
                "email_verified", true);
        OidcIdToken idToken = new OidcIdToken("token-value", now, now.plusSeconds(3600), googleClaims);
        OidcUserInfo userInfo = new OidcUserInfo(googleClaims);
        OidcUser rawPrincipal = new DefaultOidcUser(List.of(), idToken, userInfo);

        OidcUser enriched = customOidcUserService.enrich(rawPrincipal, "google");

        Object appUserId = enriched.getAttributes().get(OAuthLocalUserResolver.APP_USER_ID_ATTRIBUTE);
        assertThat(appUserId).isInstanceOf(Long.class);
        // Claims originais do Google continuam presentes.
        assertThat(enriched.getAttributes()).containsEntry("email", "sir.exiva@gmail.com");

        User created = userRepository.findById((Long) appUserId).orElseThrow();
        assertThat(created.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(created.getProviderId()).isEqualTo("108913749081234567890");
        assertThat(created.getDisplayName()).isEqualTo("Sir Exiva");
        assertThat(created.getEmail()).isEqualTo("sir.exiva@gmail.com");
    }

    @Test
    void mesmoProviderIdReaproveitaOMesmoUsuarioEmLoginsSeguintes() {
        Map<String, Object> claims = Map.of(
                "sub", "111222333444555666",
                "email", "returning@gmail.com",
                "name", "Returning User");
        OidcIdToken idToken = new OidcIdToken("t", Instant.now(), Instant.now().plusSeconds(60), claims);
        OidcUser rawPrincipal = new DefaultOidcUser(List.of(), idToken, new OidcUserInfo(claims));

        OidcUser first = customOidcUserService.enrich(rawPrincipal, "google");
        OidcUser second = customOidcUserService.enrich(rawPrincipal, "google");

        assertThat(first.getAttributes().get(OAuthLocalUserResolver.APP_USER_ID_ATTRIBUTE))
                .isEqualTo(second.getAttributes().get(OAuthLocalUserResolver.APP_USER_ID_ATTRIBUTE));
    }
}
