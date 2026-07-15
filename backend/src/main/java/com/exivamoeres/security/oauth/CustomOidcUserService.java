package com.exivamoeres.security.oauth;

import com.exivamoeres.domain.User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serviço usado no fluxo OIDC (providers COM o scope "openid" — hoje o
 * Google). O Spring Security trata OIDC como um fluxo à parte do OAuth2
 * comum: registrar só CustomOAuth2UserService (via .userService(...)) NÃO
 * cobre providers OIDC — é preciso registrar este serviço via
 * .oidcUserService(...) no SecurityConfig, senão o Spring usa o
 * OidcUserService padrão e o principal nunca ganha o atributo
 * OAuthLocalUserResolver.APP_USER_ID_ATTRIBUTE (causa do bug original: NPE no
 * OAuth2LoginSuccessHandler só para login com Google).
 */
@Service
public class CustomOidcUserService extends OidcUserService {

    private final OAuthLocalUserResolver localUserResolver;

    public CustomOidcUserService(OAuthLocalUserResolver localUserResolver) {
        this.localUserResolver = localUserResolver;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        return enrich(oidcUser, registrationId);
    }

    /** Extraído para ser testável sem precisar de uma chamada HTTP real ao provider. */
    OidcUser enrich(OidcUser oidcUser, String registrationId) {
        User user = localUserResolver.resolve(registrationId, oidcUser.getAttributes());

        // DefaultOidcUser não tem construtor que aceite um mapa de atributos
        // solto: getAttributes() é sempre derivado de idToken + userInfo. Para
        // injetar app_user_id, construímos um OidcUserInfo com todas as claims
        // originais + a nossa — como userInfo é aplicado por último no merge
        // interno do DefaultOidcUser, o atributo fica garantido no resultado.
        Map<String, Object> enrichedClaims = new LinkedHashMap<>(oidcUser.getAttributes());
        enrichedClaims.put(OAuthLocalUserResolver.APP_USER_ID_ATTRIBUTE, user.getId());
        OidcUserInfo enrichedUserInfo = new OidcUserInfo(enrichedClaims);

        return new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                oidcUser.getIdToken(),
                enrichedUserInfo,
                // "sub" é o atributo de nome padrão do OIDC (RFC — sempre presente no idToken).
                "sub");
    }
}
