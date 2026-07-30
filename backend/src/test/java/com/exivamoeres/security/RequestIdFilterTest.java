package com.exivamoeres.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O correlation ID: unitário de propósito — o que precisa de prova aqui é o
 * comportamento do filtro, e nada disso depende de banco.
 *
 * Três coisas que só um teste garante:
 *
 * <ol>
 *   <li>o id **existe durante** a cadeia (é o que faz o log sair marcado);</li>
 *   <li>o MDC é **limpo depois** — a thread volta para o pool do Tomcat, e um id
 *       vazado marcaria a requisição seguinte com o id da anterior. Log com id errado
 *       é pior que log sem id, porque parece confiável;</li>
 *   <li>id vindo de fora é **saneado**: header é entrada do cliente e log é arquivo de
 *       texto, então `\n` no valor inventaria uma linha de log falsa.</li>
 * </ol>
 */
class RequestIdFilterTest {

    private final RequestIdFilter filtro = new RequestIdFilter();

    @AfterEach
    void limparMdc() {
        MDC.clear();
    }

    /** Cadeia que anota o id visível no meio da requisição. */
    private static FilterChain espiaDoMdc(AtomicReference<String> destino) {
        return (req, res) -> destino.set(MDC.get(RequestIdFilter.MDC_KEY));
    }

    @Test
    void geraUmIdEDevolveNoHeader() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> durante = new AtomicReference<>();

        filtro.doFilter(new MockHttpServletRequest(), response, espiaDoMdc(durante));

        assertThat(durante.get()).isNotBlank();
        // O mesmo id no header: é o que permite pedir "me manda o id" e achar a
        // requisição no log de uma vez.
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo(durante.get());
    }

    @Test
    void reaproveitaOIdDeQuemChamou() throws Exception {
        String deFora = UUID.randomUUID().toString();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, deFora);
        AtomicReference<String> durante = new AtomicReference<>();

        filtro.doFilter(request, new MockHttpServletResponse(), espiaDoMdc(durante));

        // Correlação que atravessa sistemas: proxy e script conseguem amarrar a chamada
        // deles à linha de log daqui.
        assertThat(durante.get()).isEqualTo(deFora);
    }

    @Test
    void oMdcEhLimpoDepoisDaRequisicao() throws Exception {
        filtro.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), espiaDoMdc(new AtomicReference<>()));

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void oMdcEhLimpoAtehQuandoACadeiaEstoura() throws Exception {
        FilterChain queFalha = (req, res) -> {
            throw new IllegalStateException("erro no meio da requisição");
        };

        assertThatThrownBy(() ->
                filtro.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), queFalha))
                .isInstanceOf(IllegalStateException.class);

        // O `finally` existe para isto: sem ele, a requisição que estoura é justamente
        // a que deixa o id sujo para a próxima — e é a que se vai investigar.
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void idComQuebraDeLinhaEhDescartado() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // *Log injection*: o `\n` faria o valor inventar uma linha de log inteira, com
        // nível e mensagem falsos.
        request.addHeader(RequestIdFilter.HEADER, "abc\nWARN  [] falso: entrei no sistema");
        AtomicReference<String> durante = new AtomicReference<>();

        filtro.doFilter(request, new MockHttpServletResponse(), espiaDoMdc(durante));

        assertThat(durante.get()).doesNotContain("\n").doesNotContain("falso");
    }

    @Test
    void idGiganteEhDescartado() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "x".repeat(5000));
        AtomicReference<String> durante = new AtomicReference<>();

        filtro.doFilter(request, new MockHttpServletResponse(), espiaDoMdc(durante));

        // Recortado, não repassado: id é para ser lido num log, não para virar despejo
        // em toda linha.
        assertThat(durante.get()).hasSizeLessThanOrEqualTo(64);
    }

    @Test
    void idVazioViraUmGerado() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "   ");
        AtomicReference<String> durante = new AtomicReference<>();

        filtro.doFilter(request, new MockHttpServletResponse(), espiaDoMdc(durante));

        assertThat(durante.get()).isNotBlank();
    }
}
