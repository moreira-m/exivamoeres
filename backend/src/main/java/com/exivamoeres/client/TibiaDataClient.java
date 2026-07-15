package com.exivamoeres.client;

import reactor.core.publisher.Mono;

import java.util.List;

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

    /** Lista os worlds regulares válidos — usada para validar/sugerir world na UI. */
    Mono<List<String>> fetchWorlds();

    /** Busca dados de uma criatura (hoje só o ícone) pelo slug (race). */
    Mono<TibiaCreatureSnapshot> fetchCreature(String race);

    /** Catálogo completo do Bestiary (nome, race e ícone de todas as criaturas). */
    Mono<List<TibiaCreatureCatalogEntry>> fetchAllCreatures();
}
