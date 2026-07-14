package com.exivamoeres.security.oauth;

import com.exivamoeres.domain.AuthProvider;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DiscordProfileExtractor implements OAuthUserProfileExtractor {

    @Override
    public boolean supports(String registrationId) {
        return "discord".equals(registrationId);
    }

    @Override
    public OAuthUserProfile extract(Map<String, Object> attributes) {
        // Discord pode não retornar email (conta sem email verificado).
        Object email = attributes.get("email");
        Object globalName = attributes.get("global_name");
        String displayName = globalName != null
                ? globalName.toString()
                : String.valueOf(attributes.getOrDefault("username", "Jogador"));
        return new OAuthUserProfile(
                AuthProvider.DISCORD,
                String.valueOf(attributes.get("id")),
                email != null ? email.toString() : null,
                displayName);
    }
}
