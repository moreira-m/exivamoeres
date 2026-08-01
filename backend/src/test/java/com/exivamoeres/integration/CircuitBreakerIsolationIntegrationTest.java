package com.exivamoeres.integration;

import com.exivamoeres.client.TibiaDataClient;
import com.exivamoeres.domain.AuthProvider;
import com.exivamoeres.domain.User;
import com.exivamoeres.repository.UserRepository;
import com.exivamoeres.security.JwtService;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Um fluxo caindo não pode derrubar os outros (item S3).
 *
 * <p><b>O que era medido antes da mudança</b>, com um circuito só (`tibiadata`): 12 falhas
 * em {@code /v4/character} bastavam para {@code /v4/worlds} ser recusado com
 * {@code CallNotPermittedException} — e a TibiaData nem tinha sido consultada. Ou seja: o
 * job que atualiza level (até 15 chamadas por ciclo) derrubava o filtro da home e a
 * entrada em time.</p>
 *
 * <p>Os testes de isolamento abrem o circuito <b>à mão</b>
 * ({@code transitionToOpenState}) de propósito: o que está sob teste é o
 * <b>particionamento</b>, e fazer aritmética de janela deslizante em cada um deles trocaria
 * um teste claro por um teste lento e frágil. Que o circuito abre sozinho quando deve é o
 * assunto de {@code oCircuitoAbreSozinhoDepoisDeFalharOSuficiente}.</p>
 */
@AutoConfigureMockMvc
class CircuitBreakerIsolationIntegrationTest extends IntegrationTestBase {

    private static final String INTERATIVO = "tibiadata-interactive";
    private static final String BACKGROUND = "tibiadata-background";
    private static final String MUNDOS = "tibiadata-worlds";

    static final WireMockServer TIBIADATA = new WireMockServer(
            WireMockConfiguration.wireMockConfig().dynamicPort());

    static {
        TIBIADATA.start();
    }

    @DynamicPropertySource
    static void tibiaDataProperties(DynamicPropertyRegistry registry) {
        registry.add("app.tibiadata.base-url", TIBIADATA::baseUrl);
        // Backoff curto: em produção é exponencial a partir de 2s, e o teste que conta
        // recusas do circuito faria 6s de espera à toa.
        registry.add("resilience4j.retry.instances.tibiadata.wait-duration", () -> "10ms");
        registry.add("resilience4j.retry.instances.tibiadata.enable-exponential-backoff", () -> "false");
    }

    @AfterAll
    static void stopWireMock() {
        TIBIADATA.stop();
    }

    @Autowired TibiaDataClient client;
    @Autowired CircuitBreakerRegistry circuitos;
    @Autowired io.micrometer.core.instrument.MeterRegistry metricas;
    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired JwtService jwtService;

    @BeforeEach
    void estadoLimpo() {
        TIBIADATA.resetAll();
        // ⚠️ O estado do circuito é do **contexto**, não do teste: sem o reset, o primeiro
        // teste que abrisse um circuito reprovaria todos os seguintes da classe.
        circuitos.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
        personagemResponde(200);
        mundosRespondem();
    }

    // -------------------------------------------------------------- isolamento

    @Test
    void oCircuitoDoJobAbertoNaoBloqueiaQuemEstaNaTela() {
        // O cenário que motivou o item: o refresh de level é o maior gerador de falhas.
        abrir(BACKGROUND);

        assertThatThrownBy(() -> client.fetchCharacterInBackground("Sir Exiva").block())
                .isInstanceOf(CallNotPermittedException.class);
        // Mesmo endereço HTTP, orçamento de falhas diferente: a tela continua de pé.
        assertThatCode(() -> client.fetchCharacter("Sir Exiva").block())
                .doesNotThrowAnyException();
    }

    @Test
    void oCircuitoDaTelaAbertoNaoBloqueiaOJob() {
        // O contrário também importa: um pico de gente entrando em time não pode parar a
        // verificação de claim, que é o que destrava todo mundo que está esperando.
        abrir(INTERATIVO);

        assertThatCode(() -> client.fetchCharacterInBackground("Sir Exiva").block())
                .doesNotThrowAnyException();
    }

    @Test
    void oCircuitoDaTelaAbertoNaoBloqueiaAListaDeMundos() {
        // Este é exatamente o caso medido como quebrado antes da mudança.
        abrir(INTERATIVO);

        assertThatCode(() -> client.fetchWorlds().block()).doesNotThrowAnyException();
    }

    @Test
    void aListaDeMundosAbertaNaoBloqueiaAConsultaDePersonagem() {
        // Worlds tem chão no banco (S13): abrir aqui devolve "a lista de ontem". Não pode
        // custar o orçamento de quem está verificando personagem.
        abrir(MUNDOS);

        assertThatCode(() -> client.fetchCharacter("Sir Exiva").block()).doesNotThrowAnyException();
    }

    @Test
    void oCatalogoDoBootGastaOCircuitoDosJobsENaoODaTela() {
        // O import do catálogo roda no boot e é best-effort: se falhar, o site sobe com o
        // catálogo que já está no banco. Não pode levar a verificação de personagem junto.
        TIBIADATA.stubFor(get(urlPathEqualTo("/v4/creatures")).willReturn(aResponse().withStatus(500)));

        for (int i = 0; i < 4; i++) {
            try {
                client.fetchAllCreatures().block();
            } catch (RuntimeException esperada) {
                // Estamos enchendo a janela de propósito.
            }
        }

        assertThat(circuitos.circuitBreaker(BACKGROUND).getState())
                .isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(circuitos.circuitBreaker(INTERATIVO).getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
        assertThatCode(() -> client.fetchCharacter("Sir Exiva").block()).doesNotThrowAnyException();
    }

    // ------------------------------------------------------- o circuito funciona

    @Test
    void oCircuitoAbreSozinhoDepoisDeFalharOSuficiente() {
        // A premissa de tudo isto, e que nenhum teste cobria: o circuito realmente abre.
        // 4 chamadas lógicas × 3 tentativas do @Retry = 12 chamadas na janela de 10.
        personagemResponde(500);

        for (int i = 0; i < 4; i++) {
            try {
                client.fetchCharacter("Sir Exiva").block();
            } catch (RuntimeException esperada) {
                // idem
            }
        }

        assertThat(circuitos.circuitBreaker(INTERATIVO).getState())
                .isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void oRetryNaoReexecutaOFailFastDoCircuitoAberto() {
        // ⚠️ O @Retry fica **por fora** do @CircuitBreaker, então sem o `ignore-exceptions`
        // uma chamada lógica virava 3 recusas — e em produção o usuário esperava 2s + 4s de
        // backoff para receber um erro que o circuito já sabia dar na hora. Medido assim
        // antes da correção.
        abrir(INTERATIVO);
        CircuitBreaker circuito = circuitos.circuitBreaker(INTERATIVO);
        long antes = circuito.getMetrics().getNumberOfNotPermittedCalls();

        assertThatThrownBy(() -> client.fetchCharacter("Sir Exiva").block())
                .isInstanceOf(CallNotPermittedException.class);

        assertThat(circuito.getMetrics().getNumberOfNotPermittedCalls() - antes)
                .as("uma chamada lógica = uma recusa; 3 significa que o retry voltou a insistir")
                .isEqualTo(1);
    }

    // ------------------------------------------------------------ o que o usuário vê

    @Test
    void circuitoAbertoRespondeu503ComRetryAfterEnao500() throws Exception {
        // Era **500** antes desta entrega: a tela dizia "erro inesperado" para uma queda da
        // TibiaData, e o 5xx envenenava o alerta de taxa de erro — o único crítico que
        // significa "o site está quebrado por um bug".
        abrir(INTERATIVO);

        mockMvc.perform(post("/api/claims")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeUmUsuarioNovo())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"characterName":"Sir Exiva"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"))
                .andExpect(jsonPath("$.status").value(503))
                // A frase é "tente de novo daqui a pouco", não "erro inesperado": o que
                // aconteceu é temporário e o usuário precisa saber disso.
                .andExpect(jsonPath("$.message").value(
                        "Serviço temporariamente indisponível. Tente novamente em alguns minutos."));
    }

    @Test
    void osTresCircuitosExportamMetricaComONomeQueOsAlertasProcuram() {
        // ⚠️ Este teste é a ponte entre o código e `ops/observabilidade/alertas.yml`: lá
        // existem **três** regras, uma por instância, com severidades diferentes — e cada
        // uma casa por `name="tibiadata-…"`. Renomear uma instância aqui deixaria a regra
        // correspondente **calada para sempre**, e nada mais reprovaria: alerta que não
        // dispara é indistinguível de sistema saudável.
        assertThat(circuitos.getAllCircuitBreakers().stream().map(CircuitBreaker::getName))
                .contains(INTERATIVO, BACKGROUND, MUNDOS);

        for (String nome : new String[] {INTERATIVO, BACKGROUND, MUNDOS}) {
            assertThat(metricas.find("resilience4j.circuitbreaker.state").tag("name", nome).gauges())
                    .as("a série que a regra de alerta de '%s' consulta", nome)
                    .isNotEmpty();
        }
    }

    @Test
    void conexaoRecusadaResponde503EnaoTambem500() throws Exception {
        // ⚠️ Encontrado **verificando ao vivo** o resto desta entrega: com a TibiaData
        // inalcançável, as três primeiras requisições responderam **500** — só a partir da
        // quarta, com o circuito já aberto, vinha 503. Ou seja: "terceiro caiu" contava como
        // bug do site justamente na janela em que o circuito ainda não tinha aberto.
        //
        // A causa é que falha de TRANSPORTE é `WebClientRequestException`, não
        // `WebClientResponseException`, então escapava crua do cliente — violando o
        // invariante que o próprio docs/2 §6 declara ("só falha de comunicação vira
        // ExternalServiceException").
        TIBIADATA.stubFor(get(urlPathMatching("/v4/character/.*"))
                .willReturn(aResponse().withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));

        mockMvc.perform(post("/api/claims")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeUmUsuarioNovo())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"characterName":"Sir Exiva"}
                                """))
                .andExpect(status().isServiceUnavailable());
        // E o circuito da tela contou a falha — é assim que ele chega a abrir.
        assertThat(circuitos.circuitBreaker(INTERATIVO).getMetrics().getNumberOfFailedCalls())
                .isPositive();
    }

    @Test
    void conexaoRecusadaNaoDerrubaOsMundosNemOsJobs() {
        // O isolamento vale para a falha real, não só para o circuito aberto à mão.
        TIBIADATA.stubFor(get(urlPathMatching("/v4/character/.*"))
                .willReturn(aResponse().withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));

        for (int i = 0; i < 4; i++) {
            try {
                client.fetchCharacter("Sir Exiva").block();
            } catch (RuntimeException esperada) {
                // enchendo a janela
            }
        }

        assertThat(circuitos.circuitBreaker(INTERATIVO).getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThatCode(() -> client.fetchWorlds().block()).doesNotThrowAnyException();
        assertThat(circuitos.circuitBreaker(BACKGROUND).getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    // ------------------------------------------------------------------ fixtures

    private void abrir(String circuito) {
        circuitos.circuitBreaker(circuito).transitionToOpenState();
    }

    private void personagemResponde(int status) {
        TIBIADATA.stubFor(get(urlPathMatching("/v4/character/.*")).willReturn(status == 200
                ? okJson("""
                        {"character":{"character":{"name":"Sir Exiva","world":"Antica","comment":"",
                        "account_status":"Premium Account","vocation":"Elder Druid","level":500}}}""")
                : aResponse().withStatus(status)));
    }

    private void mundosRespondem() {
        TIBIADATA.stubFor(get(urlPathEqualTo("/v4/worlds")).willReturn(okJson("""
                {"worlds":{"regular_worlds":[{"name":"Antica"}]}}""")));
    }

    private String tokenDeUmUsuarioNovo() {
        User user = new User();
        user.setEmail("circuito-" + UUID.randomUUID() + "@exemplo.com");
        user.setDisplayName("Jogador do circuito");
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setPasswordHash("$2a$10$hash-irrelevante");
        return jwtService.generateAccessToken(userRepository.save(user));
    }
}
