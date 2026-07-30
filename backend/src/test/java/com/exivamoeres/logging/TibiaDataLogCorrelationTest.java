package com.exivamoeres.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.exivamoeres.client.TibiaDataApiClient;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A linha de log da TibiaData carrega o id da requisição que a provocou (item T15).
 *
 * <p><b>Por que este teste é diferente dos outros de correlação:</b> os demais afirmam que
 * o MDC está preenchido <b>durante</b> um trecho de código. Aqui o ponto é o oposto — a
 * linha sai numa <b>thread do Reactor</b> (`ctor-http-nio-*`), onde o MDC está vazio por
 * natureza. O que se prende é o transporte do contexto entre threads.</p>
 *
 * <p>O buraco apareceu ao validar o P26: procurando uma linha para correlacionar, achei
 * {@code INFO [] … tibiadata.fetch} <b>dentro</b> de uma requisição que tinha id.</p>
 *
 * <p>Sem Spring (nem banco): um {@code WebClient} apontado para WireMock e um
 * {@code ListAppender} do Logback, que guarda o mapa de MDC de cada evento — é o mesmo
 * mapa que o padrão de log usa para imprimir o marcador.</p>
 */
class TibiaDataLogCorrelationTest {

    private static final WireMockServer TIBIADATA =
            new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

    private TibiaDataApiClient client;
    private ListAppender<ILoggingEvent> linhas;
    private Logger logger;

    @BeforeAll
    static void subirWireMock() {
        TIBIADATA.start();
    }

    @AfterAll
    static void pararWireMock() {
        TIBIADATA.stop();
    }

    @BeforeEach
    void preparar() {
        TIBIADATA.resetAll();
        client = new TibiaDataApiClient(WebClient.builder().baseUrl(TIBIADATA.baseUrl()).build());
        linhas = new ListAppender<>();
        linhas.start();
        logger = (Logger) LoggerFactory.getLogger(TibiaDataApiClient.class);
        logger.addAppender(linhas);
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    void limpar() {
        logger.detachAppender(linhas);
        MDC.clear();
    }

    @Test
    void aLinhaDeSucessoCarregaOIdDaRequisicao() {
        responderComPersonagem();
        MDC.put("requestId", "req-da-tela-123");

        client.fetchCharacter("Sir Exiva").block(Duration.ofSeconds(5));

        // Sem o transporte de contexto, este mapa vem **vazio**: a linha sai de uma thread
        // do Reactor, e o MDC é ThreadLocal.
        assertThat(eventos("tibiadata.fetch"))
                .singleElement()
                .satisfies(e -> assertThat(e.getMDCPropertyMap()).containsEntry("requestId", "req-da-tela-123"));
    }

    @Test
    void aLinhaDeErroTambemCarregaOId() {
        TIBIADATA.stubFor(get(urlPathMatching("/v4/character/.*"))
                .willReturn(aResponse().withStatus(500).withBody("boom")));
        MDC.put("requestId", "req-que-falhou");

        try {
            client.fetchCharacter("Sir Exiva").block(Duration.ofSeconds(5));
        } catch (RuntimeException esperada) {
            // 500 da TibiaData vira ExternalServiceException — o assunto aqui é o log.
        }

        // É a linha que mais importa correlacionar: "a tela demorou/falhou" cruzada com
        // "a TibiaData falhou".
        assertThat(eventos("tibiadata.fetch.error"))
                .isNotEmpty()
                .allSatisfy(e -> assertThat(e.getMDCPropertyMap()).containsEntry("requestId", "req-que-falhou"));
    }

    @Test
    void oIdDoCicloDeJobTambemChega() {
        responderComPersonagem();

        // O job de claim chama a TibiaData N vezes por ciclo: é o caso em que a pergunta
        // "quais personagens este ciclo verificou?" só se responde com o id.
        LogContext.runJob(() -> client.fetchCharacter("Sir Exiva").block(Duration.ofSeconds(5)));

        assertThat(eventos("tibiadata.fetch"))
                .singleElement()
                .satisfies(e -> assertThat(e.getMDCPropertyMap().get(LogContext.JOB_RUN_ID)).startsWith("job-"));
    }

    @Test
    void semCorrelacaoAlgumaALinhaSaiIgual() {
        responderComPersonagem();

        client.fetchCharacter("Sir Exiva").block(Duration.ofSeconds(5));

        // Chamada fora de requisição e fora de job (o import do catálogo no boot, por
        // exemplo) continua registrando — só sem marcador.
        assertThat(eventos("tibiadata.fetch")).singleElement()
                .satisfies(e -> assertThat(e.getMDCPropertyMap()).doesNotContainKey("requestId"));
    }

    @Test
    void aThreadDoReactorNaoFicaSujaDepois() {
        responderComPersonagem();
        MDC.put("requestId", "req-que-vaza");

        client.fetchCharacter("Sir Exiva").block(Duration.ofSeconds(5));
        MDC.clear();
        // Segunda chamada, agora sem contexto: se a primeira tivesse deixado o id preso na
        // thread do Reactor, esta linha sairia marcada com o id da anterior — o defeito
        // que o `finally` do LogContext.with evita.
        linhas.list.clear();
        client.fetchCharacter("Sir Exiva").block(Duration.ofSeconds(5));

        assertThat(eventos("tibiadata.fetch")).singleElement()
                .satisfies(e -> assertThat(e.getMDCPropertyMap()).doesNotContainKey("requestId"));
    }

    // ----- Helpers -----

    private void responderComPersonagem() {
        TIBIADATA.stubFor(get(urlPathMatching("/v4/character/.*")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"character":{"character":{"name":"Sir Exiva","world":"Antica",
                        "comment":"","account_status":"Premium Account","vocation":"Elder Druid","level":300}}}
                        """)));
    }

    /** Os eventos cuja mensagem começa com a chave de log dada. */
    private List<ILoggingEvent> eventos(String chave) {
        return linhas.list.stream().filter(e -> e.getMessage().startsWith(chave)).toList();
    }
}
