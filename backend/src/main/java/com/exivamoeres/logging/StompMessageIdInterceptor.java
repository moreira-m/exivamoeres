package com.exivamoeres.logging;

import org.slf4j.MDC;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

/**
 * Um id por mensagem recebida pelo WebSocket, para o chat parar de sair do log sem
 * correlação (item T15).
 *
 * <p>Mensagem por socket não passa pelo {@code RequestIdFilter} — não é requisição HTTP —,
 * então tudo que o chat registrava saía com o marcador vazio. Com este interceptor, as
 * linhas de uma mesma mensagem (aceita, recusada por rate limit, erro de autorização)
 * compartilham um {@code msg-xxxxxxxx}.</p>
 *
 * <p><b>Registrado antes do interceptor de autenticação</b> ({@code WebSocketConfig}): o
 * CONNECT recusado por JWT inválido é justamente uma das linhas que se quer conseguir
 * seguir, e ela acontece dentro do interceptor de auth.</p>
 *
 * <p>O {@code afterSendCompletion} é o par obrigatório do {@code preSend}: o canal de
 * entrada usa um pool, e um id vazado marcaria a mensagem seguinte — de outro usuário —
 * com o id da anterior. Mesma armadilha do {@code RequestIdFilter}, mesmo remédio.</p>
 */
@Component
public class StompMessageIdInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        MDC.put(LogContext.MESSAGE_ID, LogContext.novoId("msg"));
        return message;
    }

    @Override
    public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
        MDC.remove(LogContext.MESSAGE_ID);
    }
}
