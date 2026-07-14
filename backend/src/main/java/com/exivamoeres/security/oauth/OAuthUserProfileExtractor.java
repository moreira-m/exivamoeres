package com.exivamoeres.security.oauth;

import java.util.Map;

/**
 * Ponto de variação entre providers OAuth: cada provider devolve os dados do
 * usuário num formato próprio. Para adicionar um provider novo (ex.: GitHub),
 * basta criar outra implementação — nada mais muda.
 */
public interface OAuthUserProfileExtractor {

    /** Id do registro no application.yml ("google", "discord", ...). */
    boolean supports(String registrationId);

    OAuthUserProfile extract(Map<String, Object> attributes);
}
