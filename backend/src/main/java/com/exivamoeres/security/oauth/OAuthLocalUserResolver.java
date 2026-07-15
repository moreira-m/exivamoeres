package com.exivamoeres.security.oauth;

import com.exivamoeres.domain.User;
import com.exivamoeres.repository.UserRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Localiza (ou cria) o User local a partir dos atributos crus de um provider
 * OAuth2/OIDC. Compartilhado entre os dois fluxos do Spring Security — OAuth2
 * comum (ex.: Discord, via CustomOAuth2UserService) e OIDC (ex.: Google, via
 * CustomOidcUserService) — porque cada provider expõe os atributos num
 * formato próprio, mas a resolução do usuário local é idêntica dali em diante.
 */
@Component
public class OAuthLocalUserResolver {

    /** Atributo interno com o id do User local, injetado nos atributos do principal. */
    public static final String APP_USER_ID_ATTRIBUTE = "app_user_id";

    private final UserRepository userRepository;
    private final List<OAuthUserProfileExtractor> extractors;

    public OAuthLocalUserResolver(UserRepository userRepository, List<OAuthUserProfileExtractor> extractors) {
        this.userRepository = userRepository;
        this.extractors = extractors;
    }

    @Transactional
    public User resolve(String registrationId, Map<String, Object> providerAttributes) {
        OAuthUserProfile profile = extractors.stream()
                .filter(e -> e.supports(registrationId))
                .findFirst()
                .orElseThrow(() -> new OAuth2AuthenticationException(
                        "Provider OAuth não suportado: " + registrationId))
                .extract(providerAttributes);
        return findOrCreateUser(profile);
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
