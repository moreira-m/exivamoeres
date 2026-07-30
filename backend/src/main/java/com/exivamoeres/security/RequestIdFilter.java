package com.exivamoeres.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Dá um identificador a cada requisição e o coloca no MDC, para toda linha de log
 * daquela requisição sair marcada (`logging.pattern.level` usa `%X{requestId}`).
 *
 * O problema que isto resolve: os logs já eram estruturados
 * (`list.join listId=… userId=…`), mas não havia como saber **quais linhas são da
 * mesma requisição**. Com tráfego concorrente, investigar um erro era juntar
 * fragmentos por horário e esperança.
 *
 * O mesmo id volta no header {@code X-Request-Id} — é o que permite pedir "me manda
 * o id que apareceu" e achar a requisição no log em uma busca.
 *
 * <h2>Ordem</h2>
 * {@code HIGHEST_PRECEDENCE} de propósito: roda **antes** do rate limit e do JWT, para
 * que 429 e 401 — que nem chegam ao controller — também apareçam identificados. Um id
 * que só existe no caminho felizmente é um id que falta justo quando se precisa dele.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    /**
     * Tamanho máximo do id aceito de fora. Um id é para ser lido por humano num log;
     * quem manda 4 KB de header não está tentando correlacionar nada.
     */
    private static final int TAMANHO_MAXIMO = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String id = idDaRequisicao(request);
        MDC.put(MDC_KEY, id);
        // No header da resposta antes de seguir a cadeia: se algo mais adiante
        // escrever o corpo e comitar a resposta, o header já foi.
        response.setHeader(HEADER, id);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Obrigatório: a thread volta para o pool do Tomcat e atenderia a próxima
            // requisição carregando o id da anterior — log com id errado é pior que log
            // sem id, porque parece confiável.
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * Reaproveita o {@code X-Request-Id} de quem chamou (proxy, script, outro serviço)
     * para a correlação atravessar sistemas; senão gera um.
     *
     * ⚠️ Valor de fora **nunca** entra cru no log. Header é entrada controlada pelo
     * cliente, e log é arquivo de texto: um `\n` no meio inventaria uma linha de log
     * falsa (*log injection*), e um valor gigante viraria despejo em toda linha. Daí o
     * recorte de tamanho e a lista de caracteres permitidos.
     */
    private String idDaRequisicao(HttpServletRequest request) {
        String recebido = request.getHeader(HEADER);
        if (recebido == null) {
            return UUID.randomUUID().toString();
        }
        String limpo = recebido.trim();
        if (limpo.length() > TAMANHO_MAXIMO) {
            limpo = limpo.substring(0, TAMANHO_MAXIMO);
        }
        // Alfanumérico, hífen e underscore: cobre UUID, ULID e o formato de trace da
        // maioria dos proxies, e não cobre mais nada.
        if (limpo.isEmpty() || !limpo.matches("[A-Za-z0-9_-]+")) {
            return UUID.randomUUID().toString();
        }
        return limpo;
    }
}
