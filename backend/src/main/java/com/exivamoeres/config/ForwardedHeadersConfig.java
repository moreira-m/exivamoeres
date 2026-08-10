package com.exivamoeres.config;

import com.exivamoeres.security.ClientIpFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ForwardedHeaderFilter;

/**
 * Quem lê os cabeçalhos de proxy, e <b>em que ordem</b>.
 *
 * <p>O Spring Boot registraria o {@link ForwardedHeaderFilter} sozinho (era o
 * {@code server.forward-headers-strategy: framework} no application.yml), mas com
 * {@code Ordered.HIGHEST_PRECEDENCE} — e aí <b>nada</b> pode rodar antes dele, porque
 * não existe ordem menor que essa. Como ele <b>apaga</b> o {@code X-Forwarded-For} do
 * request, o endereço real do cliente sumia antes de o rate limit poder olhar (ver
 * {@link ClientIpFilter}).
 *
 * <p>Registrando os dois aqui, a ordem é nossa: o {@link ClientIpFilter} lê o cabeçalho
 * cru primeiro, o {@link ForwardedHeaderFilter} faz o trabalho dele em seguida — e o
 * {@code redirect_uri} do OAuth continua saindo em {@code https} em produção, que é o
 * motivo de ele existir neste projeto.
 *
 * <p>⚠️ O {@code server.forward-headers-strategy} foi <b>removido</b> do application.yml
 * de propósito: com o filtro registrado aqui, a propriedade não teria mais efeito e
 * passaria a descrever um comportamento que não é mais o dela — configuração que mente é
 * pior que configuração ausente.
 */
@Configuration
public class ForwardedHeadersConfig {

    @Bean
    public FilterRegistrationBean<ClientIpFilter> clientIpFilter() {
        FilterRegistrationBean<ClientIpFilter> registro =
                new FilterRegistrationBean<>(new ClientIpFilter());
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registro;
    }

    /**
     * Mesma configuração que o Spring Boot usaria, um degrau depois do
     * {@link ClientIpFilter}. Declarar este bean faz o Boot recuar do registro
     * automático ({@code @ConditionalOnMissingFilterBean}).
     */
    @Bean
    public FilterRegistrationBean<ForwardedHeaderFilter> forwardedHeaderFilter() {
        FilterRegistrationBean<ForwardedHeaderFilter> registro =
                new FilterRegistrationBean<>(new ForwardedHeaderFilter());
        registro.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registro;
    }
}
