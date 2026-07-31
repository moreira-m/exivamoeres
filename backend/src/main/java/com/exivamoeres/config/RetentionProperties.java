package com.exivamoeres.config;

import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Por quanto tempo o banco guarda o que já não serve para ninguém (item S8).
 *
 * <p>Duas tabelas crescem sem teto porque nada nunca as limpa: {@code refresh_tokens}
 * (uma linha morta a cada refresh, por causa da rotação) e {@code notifications}
 * (uma linha por evento, para sempre). O plano do Postgres é pago por armazenamento,
 * e a limpeza é trivial agora e chata depois.</p>
 *
 * <p>⚠️ Isto <b>não</b> contradiz o "nunca deletar histórico" das convenções: o que
 * some aqui não é histórico. Refresh token é <b>credencial</b> — depois de vencido não
 * autentica ninguém, e o que ele registra ("fulano entrou") já está em log. Notificação
 * é <b>aviso entregue</b> — o fato que ela anunciava continua no time, na participação
 * e no chat. O que é histórico de verdade (mensagem, participação, claim) <b>não é
 * tocado por este job</b>.</p>
 *
 * <p>⚠️ Os {@code @PositiveOrZero} não são enfeite: uma janela <b>negativa</b> inverte o
 * corte e passa a apagar o que ainda vale — todo mundo deslogado de uma vez, notificação
 * sumindo antes de ser lida. É erro de digitação em variável de ambiente com raio de
 * alcance grande demais para se descobrir rodando; com o {@code @Validated} ele derruba o
 * boot dizendo qual propriedade está errada.</p>
 */
@Validated
@ConfigurationProperties(prefix = "app.retention")
public record RetentionProperties(
        /** De quanto em quanto tempo o job roda. Diário basta: o volume diário é pequeno. */
        Duration interval,
        /**
         * Dias de carência <b>depois do vencimento</b> do refresh token.
         *
         * Não é "quanto o token vive" (isso é {@code app.jwt.refresh-token-days}): é
         * quanto o registro morto fica no banco depois disso. O motivo de existir
         * carência em vez de apagar no vencimento está em {@code RetentionServiceImpl}.
         */
        @PositiveOrZero int expiredRefreshTokenDays,
        /** Dias que uma notificação <b>lida</b> fica guardada. Não-lida nunca é apagada. */
        @PositiveOrZero int readNotificationDays
) {

    public Duration expiredRefreshTokenGrace() {
        return Duration.ofDays(expiredRefreshTokenDays);
    }

    public Duration readNotificationRetention() {
        return Duration.ofDays(readNotificationDays);
    }
}
