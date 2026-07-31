package com.exivamoeres.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Janela de retenção negativa <b>derruba o boot</b> (item S8).
 *
 * <p>Por que isto merece teste: uma janela negativa inverte o corte. Em vez de "apaga o
 * que venceu há mais de 30 dias", vira "apaga o que vence nos próximos 30" — todo mundo
 * deslogado de uma vez, notificação sumindo antes de ser lida. É um erro de digitação em
 * variável de ambiente, do tipo que só aparece <b>rodando</b>, e o dano já estaria feito
 * quando aparecesse.</p>
 *
 * <p>Contexto de mentira (sem banco, sem Spring Boot de verdade): a regra é do
 * `@Validated`, e prová-la não precisa de nada além do binder.</p>
 */
class RetentionPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations
                    .of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(ConfiguracaoDeTeste.class);

    @Test
    void configuracaoValidaSobe() {
        runner.withPropertyValues(
                        "app.retention.interval=24h",
                        "app.retention.expired-refresh-token-days=30",
                        "app.retention.read-notification-days=90")
                .run(contexto -> {
                    assertThat(contexto).hasNotFailed();
                    RetentionProperties props = contexto.getBean(RetentionProperties.class);
                    assertThat(props.expiredRefreshTokenGrace().toDays()).isEqualTo(30);
                    assertThat(props.readNotificationRetention().toDays()).isEqualTo(90);
                });
    }

    @Test
    void carenciaNegativaDeTokenNaoDeixaOAppSubir() {
        runner.withPropertyValues(
                        "app.retention.interval=24h",
                        "app.retention.expired-refresh-token-days=-1",
                        "app.retention.read-notification-days=90")
                // `hasStackTraceContaining` e não `hasMessageContaining`: o Spring
                // embrulha o erro de bind, e o nome da propriedade só aparece na
                // BindValidationException lá no fundo da cadeia de causas. É o nome
                // que importa — ele é a diferença entre "arrume a variável de
                // ambiente" e "o app não sobe, socorro".
                .run(contexto -> assertThat(contexto).hasFailed()
                        .getFailure().hasStackTraceContaining("expiredRefreshTokenDays"));
    }

    @Test
    void retencaoNegativaDeNotificacaoNaoDeixaOAppSubir() {
        runner.withPropertyValues(
                        "app.retention.interval=24h",
                        "app.retention.expired-refresh-token-days=30",
                        "app.retention.read-notification-days=-90")
                .run(contexto -> assertThat(contexto).hasFailed()
                        .getFailure().hasStackTraceContaining("readNotificationDays"));
    }

    @EnableConfigurationProperties(RetentionProperties.class)
    static class ConfiguracaoDeTeste {
    }
}
