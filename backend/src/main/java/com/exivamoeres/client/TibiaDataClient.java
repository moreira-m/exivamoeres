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
     * Busca o personagem pelo nome, <b>com alguém esperando na tela</b> (criar claim,
     * entrar em time). O Mono emite erro (ExternalServiceException) apenas em falha de
     * comunicação após os retries; personagem inexistente é resposta válida
     * (found = false).
     *
     * <p>⚠️ Gasta o circuito <b>tibiadata-interactive</b>. Em job, use
     * {@link #fetchCharacterInBackground(String)} — os dois batem no mesmo endereço, e a
     * diferença é de qual orçamento de falhas a chamada sai (item S3).</p>
     */
    Mono<TibiaCharacterSnapshot> fetchCharacter(String characterName);

    /**
     * O mesmo que {@link #fetchCharacter(String)}, mas para quem roda <b>sem ninguém
     * esperando</b>: os jobs de verificação de claim e de refresh de level.
     *
     * <p>Existe por causa do volume: o refresh de level sozinho faz até 15 chamadas por
     * ciclo, então é ele quem acumula falhas primeiro numa instabilidade da TibiaData. No
     * circuito único, isso <b>bloqueava quem estava tentando entrar num time</b>. Aqui a
     * conta é separada: o job degrada e tenta no próximo ciclo, e a tela continua de pé.</p>
     */
    Mono<TibiaCharacterSnapshot> fetchCharacterInBackground(String characterName);

    /** Lista os worlds regulares válidos — usada para validar/sugerir world na UI. */
    Mono<List<String>> fetchWorlds();

    /** Catálogo completo do Bestiary (nome, race e ícone de todas as criaturas). */
    Mono<List<TibiaCreatureCatalogEntry>> fetchAllCreatures();
}
