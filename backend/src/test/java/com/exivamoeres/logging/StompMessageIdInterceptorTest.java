package com.exivamoeres.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O id por mensagem do chat (item T15).
 *
 * <p>Mensagem por WebSocket não passa pelo {@code RequestIdFilter}, então tudo que o chat
 * registra saía com o marcador vazio. O que estes casos prendem é o par
 * {@code preSend}/{@code afterSendCompletion}: o canal de entrada usa um pool, e um id
 * vazado marcaria a mensagem seguinte — de outro usuário — com o id da anterior.</p>
 */
class StompMessageIdInterceptorTest {

    private final StompMessageIdInterceptor interceptor = new StompMessageIdInterceptor();
    private final Message<String> mensagem = new GenericMessage<>("oi pessoal");

    @AfterEach
    void limparMdc() {
        MDC.clear();
    }

    @Test
    void aMensagemGanhaUmIdComPrefixo() {
        interceptor.preSend(mensagem, null);

        // O prefixo diz a origem na própria linha de log: `msg-…` não se confunde com o
        // UUID de uma requisição HTTP nem com o `job-…` de um ciclo.
        assertThat(MDC.get(LogContext.MESSAGE_ID)).startsWith("msg-");
    }

    @Test
    void duasMensagensTemIdsDiferentes() {
        interceptor.preSend(mensagem, null);
        String primeira = MDC.get(LogContext.MESSAGE_ID);
        interceptor.afterSendCompletion(mensagem, null, true, null);

        interceptor.preSend(mensagem, null);

        assertThat(MDC.get(LogContext.MESSAGE_ID)).isNotEqualTo(primeira);
    }

    @Test
    void oIdSaiQuandoAMensagemTermina() {
        interceptor.preSend(mensagem, null);

        interceptor.afterSendCompletion(mensagem, null, true, null);

        assertThat(MDC.get(LogContext.MESSAGE_ID)).isNull();
    }

    @Test
    void oIdSaiTambemQuandoOEnvioFalha() {
        interceptor.preSend(mensagem, null);

        // Mensagem recusada (rate limit, autorização) é o caso em que mais se quer o id —
        // e é justamente quando o `sent=false` chega aqui.
        interceptor.afterSendCompletion(mensagem, null, false, new IllegalStateException("recusada"));

        assertThat(MDC.get(LogContext.MESSAGE_ID)).isNull();
    }

    @Test
    void aMensagemSegueIntacta() {
        Message<?> devolvida = interceptor.preSend(mensagem, null);

        // Interceptor de log não mexe no conteúdo: quem transforma mensagem é o de
        // autenticação, que roda **depois** deste (ver WebSocketConfig).
        assertThat(devolvida).isSameAs(mensagem);
    }
}
