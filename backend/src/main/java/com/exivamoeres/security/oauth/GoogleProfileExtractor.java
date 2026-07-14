package com.exivamoeres.security.oauth;

import com.exivamoeres.domain.AuthProvider;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class GoogleProfileExtractor implements OAuthUserProfileExtractor {

    @Override
    public boolean supports(String registrationId) {
        return "google".equals(registrationId);
    }

    @Override
    public OAuthUserProfile extract(Map<String, Object> attributes) {
        return new OAuthUserProfile(
                AuthProvider.GOOGLE,
                (String) attributes.get("sub"),
                (String) attributes.get("email"),
                (String) attributes.getOrDefault("name", "Jogador"));
    }
}
