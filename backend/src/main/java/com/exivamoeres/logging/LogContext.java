package com.exivamoeres.logging;

import org.slf4j.MDC;

import java.util.Map;
import java.util.UUID;

/**
 * As chaves de correlação do log e as duas operações que as movimentam.
 *
 * <p>O {@code requestId} do {@code RequestIdFilter} cobre requisição HTTP. Quem não nasce
 * de uma saía com o marcador vazio — e é justamente aí que "quais linhas são da mesma
 * coisa?" fica difícil de responder:</p>
 *
 * <table>
 *   <tr><th>Origem</th><th>Chave</th><th>Formato</th></tr>
 *   <tr><td>Requisição HTTP</td><td>{@code requestId}</td><td>o id do cabeçalho, ou um UUID</td></tr>
 *   <tr><td>Ciclo de job {@code @Scheduled}</td><td>{@code jobRunId}</td><td>{@code job-xxxxxxxx}</td></tr>
 *   <tr><td>Mensagem do chat (STOMP)</td><td>{@code msgId}</td><td>{@code msg-xxxxxxxx}</td></tr>
 * </table>
 *
 * <p><b>Chaves separadas, e não uma só</b>: {@code %X{requestId:-}} continua significando
 * "requisição HTTP", e misturar origens na mesma chave faria o log mentir sobre o que é o
 * quê. O <b>prefixo</b> no valor resolve o outro lado do problema — olhando a linha, dá
 * para saber a origem sem decorar qual chave o padrão imprimiu.</p>
 */
public final class LogContext {

    /** Um ciclo de job {@code @Scheduled}. */
    public static final String JOB_RUN_ID = "jobRunId";

    /** Uma mensagem recebida pelo WebSocket (STOMP). */
    public static final String MESSAGE_ID = "msgId";

    private LogContext() {
    }

    /**
     * Roda o ciclo de um job com um id próprio no MDC, e limpa no fim.
     *
     * <p>O {@code finally} não é zelo abstrato: a thread volta para o pool do agendador, e
     * um id vazado marcaria o ciclo seguinte — ou uma requisição HTTP — com o id de outra
     * coisa. Log com id errado é pior que log sem id, porque parece confiável.</p>
     *
     * <p>Exceção <b>passa direto</b>: os três jobs relançam de propósito, para a métrica
     * {@code tasks.scheduled.execution} marcar {@code outcome=ERROR} num ciclo que
     * falhou (ver {@code new-features/observabilidade.md}).</p>
     */
    public static void runJob(Runnable ciclo) {
        MDC.put(JOB_RUN_ID, novoId("job"));
        try {
            ciclo.run();
        } finally {
            MDC.remove(JOB_RUN_ID);
        }
    }

    /** O contexto de log da thread atual, para levar a outra (ver {@link #with}). */
    public static Map<String, String> capture() {
        return MDC.getCopyOfContextMap();
    }

    /**
     * Executa a ação com o contexto capturado em outra thread, restaurando o que havia
     * antes.
     *
     * <p><b>Para que serve:</b> o MDC é um {@code ThreadLocal}, e o cliente da TibiaData é
     * reativo — os callbacks de log rodam numa thread do Reactor, não na da requisição.
     * Sem isto, a linha que diz quanto a API externa demorou sai <b>sem</b> id, mesmo
     * dentro de uma requisição que tem um. Foi assim que o buraco apareceu, ao validar o
     * P26.</p>
     *
     * <p>Restaurar (em vez de só limpar) importa porque a thread do Reactor é
     * compartilhada: ela pode estar servindo outra coisa entre um callback e outro.</p>
     */
    public static void with(Map<String, String> contexto, Runnable acao) {
        if (contexto == null || contexto.isEmpty()) {
            acao.run();
            return;
        }
        Map<String, String> anterior = MDC.getCopyOfContextMap();
        MDC.setContextMap(contexto);
        try {
            acao.run();
        } finally {
            if (anterior == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(anterior);
            }
        }
    }

    /** Id curto e legível: o prefixo diz a origem sem precisar olhar a chave do MDC. */
    public static String novoId(String prefixo) {
        return prefixo + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
