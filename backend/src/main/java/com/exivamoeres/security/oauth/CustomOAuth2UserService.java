package com.exivamoeres.security.oauth;

import com.exivamoeres.domain.User;
import com.exivamoeres.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Após o provider autenticar o usuário, localiza (ou cria) a conta local
 * correspondente e injeta o id interno nos atributos para o success handler
 * emitir o JWT.
 */
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    /** Atributo interno com o id do User local, lido pelo success handler. */
    public static final String APP_USER_ID_ATTRIBUTE = "app_user_id";

    private final UserRepository userRepository;
    private final List<OAuthUserProfileExtractor> extractors;

    public CustomOAuth2UserService(UserRepository userRepository,
                                   List<OAuthUserProfileExtractor> extractors) {
        this.userRepository = userRepository;
        this.extractors = extractors;
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User oauthUser = super.loadUser(request);
        String registrationId = request.getClientRegistration().getRegistrationId();

        OAuthUserProfile profile = extractors.stream()
                .filter(e -> e.supports(registrationId))
                .findFirst()
                .orElseThrow(() -> new OAuth2AuthenticationException(
                        "Provider OAuth não suportado: " + registrationId))
                .extract(oauthUser.getAttributes());

        User user = findOrCreateUser(profile);

        Map<String, Object> attributes = new HashMap<>(oauthUser.getAttributes());
        attributes.put(APP_USER_ID_ATTRIBUTE, user.getId());
        String nameAttribute = request.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();
        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")), attributes, nameAttribute);
    }

    private User findOrCreateUser(OAuthUserProfile profile) {
        return userRepository
                .findByAuthProviderAndProviderId(profile.provider(), profile.providerId())
                .orElseGet(() -> createUser(profile));
    }

    private User createUser(OAuthUserProfile profile) {
        User user = new User();
        user.setAuthProvider(profile.provider());
        user.setProviderId(profile.providerId());
        user.setDisplayName(profile.displayName());
        // Só associa o email se nenhuma conta local já o usa — vincular contas
        // pelo email automaticamente permitiria tomada de conta caso o provider
        // não verifique o email.
        if (profile.email() != null && !userRepository.existsByEmailIgnoreCase(profile.email())) {
            user.setEmail(profile.email());
        }
        return userRepository.save(user);
    }
}
