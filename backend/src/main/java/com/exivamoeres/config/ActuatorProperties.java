package com.exivamoeres.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Acesso aos endpoints do Actuator fora do {@code /actuator/health}.
 *
 * Métricas não são dado de usuário: entregam rotas mais chamadas, taxa de erro,
 * pool de conexões e o volume do site. Por isso quem lê não é um usuário
 * autenticado do produto, é um coletor com segredo próprio.
 *
 * Vazio (o padrão) = ninguém acessa nada além do health.
 */
@ConfigurationProperties(prefix = "app.actuator")
public record ActuatorProperties(
        /** Segredo esperado no header {@code X-Actuator-Token}. */
        String token
) {
}
