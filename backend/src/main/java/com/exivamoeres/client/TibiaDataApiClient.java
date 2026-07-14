package com.exivamoeres.client;

import com.exivamoeres.client.dto.TibiaDataCharacterResponse;
import com.exivamoeres.domain.exception.ExternalServiceException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Implementação HTTP da TibiaData API (v4).
 *
 * Resiliência (instância "tibiadata" no application.yml):
 * - @Retry: backoff exponencial — falha de rede temporária não pode deixar um
 *   claim sem checagem até o próximo ciclo;
 * - @CircuitBreaker: se a TibiaData cair, paramos de martelar a API e o job
 *   falha rápido (claims permanecem PENDING e serão rechecados depois).
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
    @CircuitBreaker(name = "tibiadata")
    public Mono<TibiaCharacterSnapshot> fetchCharacter(String characterName) {
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
                .doOnSuccess(snapshot -> log.info(
                        "tibiadata.fetch name='{}' found={} world={}",
                        characterName, snapshot.found(), snapshot.world()))
                .doOnError(error -> log.warn(
                        "tibiadata.fetch.error name='{}' error={}",
                        characterName, error.toString()));
    }

    private TibiaCharacterSnapshot toSnapshot(TibiaDataCharacterResponse response) {
        if (!response.hasCharacter()) {
            return TibiaCharacterSnapshot.notFound();
        }
        var data = response.character().character();
        return new TibiaCharacterSnapshot(true, data.name(), data.world(), data.comment());
    }
}
