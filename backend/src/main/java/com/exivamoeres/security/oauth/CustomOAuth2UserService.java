package com.exivamoeres.security.oauth;

import com.exivamoeres.domain.User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serviço usado no fluxo OAuth2 comum (providers SEM o scope "openid" — hoje
 * só o Discord). Providers OIDC (Google) usam CustomOidcUserService: o Spring
 * Security roteia para um serviço ou outro conforme o client registration.
 *
 * Localiza (ou cria) a conta local correspondente e injeta o id interno nos
 * atributos para o success handler emitir o JWT.
 */
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final OAuthLocalUserResolver localUserResolver;

    public CustomOAuth2UserService(OAuthLocalUserResolver localUserResolver) {
        this.localUserResolver = localUserResolver;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User oauthUser = super.loadUser(request);
        String registrationId = request.getClientRegistration().getRegistrationId();
        String nameAttribute = request.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();
        return enrich(oauthUser, registrationId, nameAttribute);
    }

    /** Extraído para ser testável sem precisar de uma chamada HTTP real ao provider. */
    OAuth2User enrich(OAuth2User oauthUser, String registrationId, String nameAttribute) {
        User user = localUserResolver.resolve(registrationId, oauthUser.getAttributes());

        Map<String, Object> attributes = new HashMap<>(oauthUser.getAttributes());
        attributes.put(OAuthLocalUserResolver.APP_USER_ID_ATTRIBUTE, user.getId());
        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")), attributes, nameAttribute);
    }
}
