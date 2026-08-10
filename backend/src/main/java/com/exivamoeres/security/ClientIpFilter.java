package com.exivamoeres.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * Descobre <b>de quem</b> é a requisição e guarda o endereço num atributo, para
 * quem precisar dele depois na cadeia — hoje, o {@link RateLimitFilter}.
 *
 * <p><b>Por que não basta perguntar ao request.</b> Este projeto roda atrás do proxy
 * do Railway, então o endereço real chega no {@code X-Forwarded-For} — e esse cabeçalho
 * é escrito pelo <b>cliente</b> até o proxy acrescentar a parte dele. Cada salto
 * <b>acrescenta ao fim</b> o endereço de quem falou com ele: a <b>última</b> entrada é a
 * que o nosso proxy viu; as anteriores são as que o cliente quis que a gente lesse.
 *
 * <p>O {@code ForwardedHeaderFilter} do Spring resolve o <i>scheme</i> (é dele que sai o
 * {@code https} do {@code redirect_uri} do OAuth), mas para o endereço ele usa a
 * <b>primeira</b> entrada — a forjada — e <b>remove o cabeçalho</b> do request. Ou seja,
 * quem rodasse depois dele não teria nem como conferir. Por isso este filtro roda
 * <b>antes</b> (ver {@code ForwardedHeadersConfig}) e é o único lugar que lê o cabeçalho
 * cru.
 *
 * <p>Sem isto, mandar um {@code X-Forwarded-For} diferente a cada tentativa dava um balde
 * de rate limit novo por tentativa: o limite de força bruta no login e o de criação de
 * conta anônima — os dois motivos de o RateLimitFilter existir — não valiam nada.
 *
 * <p>⚠️ Vale para exatamente <b>um</b> proxy à frente, que é o deploy de hoje. Com dois
 * (um CDN na frente, por exemplo), a última entrada passa a ser o CDN e todos os clientes
 * caem no mesmo balde — se isso acontecer, o número de saltos confiáveis vira
 * configuração em vez de continuar implícito aqui.
 */
public class ClientIpFilter implements Filter {

    private static final String FORWARDED_FOR = "X-Forwarded-For";
    private static final String ATTRIBUTE = ClientIpFilter.class.getName() + ".clientIp";

    /**
     * O endereço do cliente, para quem roda depois deste filtro. Cai no endereço da
     * conexão se o atributo não estiver lá — o que só acontece fora da cadeia HTTP normal.
     */
    public static String of(HttpServletRequest request) {
        Object capturado = request.getAttribute(ATTRIBUTE);
        return capturado instanceof String ip ? ip : request.getRemoteAddr();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest http) {
            http.setAttribute(ATTRIBUTE, resolve(http));
        }
        chain.doFilter(request, response);
    }

    private String resolve(HttpServletRequest request) {
        String forwarded = request.getHeader(FORWARDED_FOR);
        if (!StringUtils.hasText(forwarded)) {
            return request.getRemoteAddr();
        }
        String[] saltos = forwarded.split(",");
        String ultimo = saltos[saltos.length - 1].trim();
        // Cabeçalho terminando em vírgula ou só com espaços: cai no endereço da conexão,
        // que é o único dado que ninguém do outro lado escreve.
        return ultimo.isEmpty() ? request.getRemoteAddr() : ultimo;
    }
}
