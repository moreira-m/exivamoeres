package com.exivamoeres.client;

import reactor.core.publisher.Mono;

/**
 * Abstração sobre a TibiaData API. Interface própria para que os services
 * dependam do contrato, não do transporte — e para os testes trocarem a
 * implementação por stub/WireMock sem tocar na regra de negócio.
 */
public interface TibiaDataClient {

    /**
     * Busca o personagem pelo nome. O Mono emite erro
     * (ExternalServiceException) apenas em falha de comunicação após os
     * retries; personagem inexistente é resposta válida (found = false).
     */
    Mono<TibiaCharacterSnapshot> fetchCharacter(String characterName);
}
