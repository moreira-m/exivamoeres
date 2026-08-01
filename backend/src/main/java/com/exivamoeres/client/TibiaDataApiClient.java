package com.exivamoeres.client;

import com.exivamoeres.client.dto.TibiaDataCharacterResponse;
import com.exivamoeres.client.dto.TibiaDataCreaturesResponse;
import com.exivamoeres.client.dto.TibiaDataWorldsResponse;
import com.exivamoeres.domain.exception.ExternalServiceException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import com.exivamoeres.logging.LogContext;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Implementação HTTP da TibiaData API (v4).
 *
 * Resiliência (blocos resilience4j.* no application.yml):
 * - @Retry (instância "tibiadata", uma só): backoff exponencial — falha de rede
 *   temporária não pode deixar um claim sem checagem até o próximo ciclo. Retry não
 *   guarda estado entre chamadas, então não há o que isolar aqui;
 * - @CircuitBreaker (<b>três</b> instâncias): se a TibiaData cair, paramos de martelar
 *   a API e a chamada falha rápido.
 *
 * ⚠️ Os três circuitos são o item S3, e a razão é medida: com um circuito só, 12 falhas
 * em /v4/character bastavam para bloquear /v4/worlds com CallNotPermittedException — um
 * fluxo derrubava os outros. Quem gasta qual:
 *
 * <pre>
 *   tibiadata-interactive → fetchCharacter          (alguém esperando na tela)
 *   tibiadata-background  → fetchCharacterInBackground, fetchAllCreatures (jobs e boot)
 *   tibiadata-worlds      → fetchWorlds             (tem chão no banco, S13)
 * </pre>
 *
 * ⚠️ <b>Método novo aqui precisa escolher um circuito conscientemente.</b> O critério é
 * "quem espera pela resposta", não qual endpoint é chamado: fetchCharacter e
 * fetchCharacterInBackground são a MESMA requisição HTTP em circuitos diferentes.
 */
@Component
@Slf4j
public class TibiaDataApiClient implements TibiaDataClient {

    private final WebClient webClient;

    public TibiaDataApiClient(WebClient tibiaDataWebClient) {
        this.webClient = tibiaDataWebClient;
    }

    @Override
    @Retry(name = "tibiadata")
    @CircuitBreaker(name = "tibiadata-interactive")
    public Mono<TibiaCharacterSnapshot> fetchCharacter(String characterName) {
        return characterRequest(characterName);
    }

    @Override
    @Retry(name = "tibiadata")
    @CircuitBreaker(name = "tibiadata-background")
    public Mono<TibiaCharacterSnapshot> fetchCharacterInBackground(String characterName) {
        return characterRequest(characterName);
    }

    /**
     * A requisição em si, sem anotação nenhuma — de propósito.
     *
     * ⚠️ Anotação em método privado (ou chamado de dentro da própria classe) <b>não vale</b>:
     * o aspecto do resilience4j age no proxy do Spring. Por isso os dois públicos acima
     * carregam cada um o seu circuito e este aqui só monta o Mono.
     */
    private Mono<TibiaCharacterSnapshot> characterRequest(String characterName) {
        // O contexto de log **da thread que chamou** (a da requisição HTTP ou a do job).
        // Os callbacks abaixo rodam em thread do Reactor, onde o MDC está vazio: sem
        // levá-lo junto, a linha que diz quanto a TibiaData demorou sai sem correlação —
        // justamente a que se quer cruzar com "a tela demorou". Ver NEXT_STEPS T15.
        Map<String, String> contexto = LogContext.capture();
        // pathSegment aplica URL-encoding — nomes do Tibia contêm espaços
        // (ex.: "Kharsek The Great") e viram %20 na URL.
        return webClient.get()
                .uri(builder -> builder.pathSegment("v4", "character", characterName).build())
                .retrieve()
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        response.createException().map(e ->
                                new ExternalServiceException("TibiaData respondeu " + response.statusCode(), e)))
                .bodyToMono(TibiaDataCharacterResponse.class)
                .map(this::toSnapshot)
                // 404/4xx da TibiaData = personagem inexistente, não erro de infra.
                .onErrorResume(org.springframework.web.reactive.function.client.WebClientResponseException.class,
                        e -> e.getStatusCode().is4xxClientError()
                                ? Mono.just(TibiaCharacterSnapshot.notFound())
                                : Mono.error(new ExternalServiceException("Falha ao consultar TibiaData", e)))
                // ⚠️ Falha de TRANSPORTE (conexão recusada, DNS, reset): não é
                // WebClientResponseException, então o `onErrorResume` acima não a pega e ela
                // escapava crua — virando **500** no handler. Medido ao vivo com a TibiaData
                // inalcançável: 3 requisições seguidas responderam 500 antes de o circuito
                // abrir. É falha de comunicação, logo ExternalServiceException → 503.
                .onErrorMap(WebClientRequestException.class,
                        e -> new ExternalServiceException("Falha ao consultar TibiaData", e))
                .doOnSuccess(snapshot -> LogContext.with(contexto, () -> log.info(
                        "tibiadata.fetch name='{}' found={} world={}",
                        characterName, snapshot.found(), snapshot.world())))
                .doOnError(error -> LogContext.with(contexto, () -> log.warn(
                        "tibiadata.fetch.error name='{}' error={}",
                        characterName, error.toString())));
    }

    private TibiaCharacterSnapshot toSnapshot(TibiaDataCharacterResponse response) {
        if (!response.hasCharacter()) {
            return TibiaCharacterSnapshot.notFound();
        }
        var data = response.character().character();
        return new TibiaCharacterSnapshot(
                true, data.name(), data.world(), data.comment(), data.accountStatus(), data.vocation(), data.level());
    }

    @Override
    @Retry(name = "tibiadata")
    @CircuitBreaker(name = "tibiadata-worlds")
    public Mono<List<String>> fetchWorlds() {
        Map<String, String> contexto = LogContext.capture();
        return webClient.get()
                .uri(builder -> builder.pathSegment("v4", "worlds").build())
                .retrieve()
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        response.createException().map(e ->
                                new ExternalServiceException("TibiaData respondeu " + response.statusCode(), e)))
                .bodyToMono(TibiaDataWorldsResponse.class)
                .map(TibiaDataWorldsResponse::names)
                // ⚠️ Falha de TRANSPORTE (conexão recusada, DNS, reset): não é
                // WebClientResponseException, então o `onErrorResume` acima não a pega e ela
                // escapava crua — virando **500** no handler. Medido ao vivo com a TibiaData
                // inalcançável: 3 requisições seguidas responderam 500 antes de o circuito
                // abrir. É falha de comunicação, logo ExternalServiceException → 503.
                .onErrorMap(WebClientRequestException.class,
                        e -> new ExternalServiceException("Falha ao consultar TibiaData", e))
                .doOnError(error -> LogContext.with(contexto, () ->
                        log.warn("tibiadata.worlds.error error={}", error.toString())));
    }

    @Override
    @Retry(name = "tibiadata")
    @CircuitBreaker(name = "tibiadata-background")
    public Mono<List<TibiaCreatureCatalogEntry>> fetchAllCreatures() {
        Map<String, String> contexto = LogContext.capture();
        return webClient.get()
                .uri(builder -> builder.pathSegment("v4", "creatures").build())
                .retrieve()
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        response.createException().map(e ->
                                new ExternalServiceException("TibiaData respondeu " + response.statusCode(), e)))
                .bodyToMono(TibiaDataCreaturesResponse.class)
                .map(response -> response.entries().stream()
                        .map(e -> new TibiaCreatureCatalogEntry(e.name(), e.race(), e.imageUrl()))
                        .toList())
                // 4xx = sem dados, não falha de infra: não conta como falha no circuito.
                .onErrorResume(org.springframework.web.reactive.function.client.WebClientResponseException.class,
                        e -> e.getStatusCode().is4xxClientError()
                                ? Mono.just(List.<TibiaCreatureCatalogEntry>of())
                                : Mono.error(new ExternalServiceException("Falha ao consultar TibiaData", e)))
                // ⚠️ Falha de TRANSPORTE (conexão recusada, DNS, reset): não é
                // WebClientResponseException, então o `onErrorResume` acima não a pega e ela
                // escapava crua — virando **500** no handler. Medido ao vivo com a TibiaData
                // inalcançável: 3 requisições seguidas responderam 500 antes de o circuito
                // abrir. É falha de comunicação, logo ExternalServiceException → 503.
                .onErrorMap(WebClientRequestException.class,
                        e -> new ExternalServiceException("Falha ao consultar TibiaData", e))
                .doOnSuccess(list -> LogContext.with(contexto, () ->
                        log.info("tibiadata.creatures.fetched count={}", list.size())))
                .doOnError(error -> LogContext.with(contexto, () ->
                        log.warn("tibiadata.creatures.error error={}", error.toString())));
    }
}
