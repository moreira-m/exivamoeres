package com.exivamoeres.integration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.exivamoeres.client.TibiaDataClient;
import com.exivamoeres.config.CacheConfig;
import com.exivamoeres.domain.World;
import com.exivamoeres.domain.exception.ExternalServiceException;
import com.exivamoeres.repository.WorldRepository;
import com.exivamoeres.service.WorldService;
import com.exivamoeres.service.impl.WorldServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A lista de mundos sobrevive à TibiaData fora do ar (item S13).
 *
 * <p>O filtro de mundos da home é o primeiro controle que todo visitante vê, e ele
 * dependia da API externa estar de pé <b>no momento em que o cache esquenta</b> — o que,
 * com cache em memória, é <b>todo restart</b>. Uma queda da TibiaData durante um deploy
 * abria o site sem filtro nenhum.</p>
 *
 * <p>O que estes testes prendem é a diferença entre "a lista de ontem" e "nenhuma lista",
 * e os dois cuidados que fazem o fallback não virar um problema novo: ele <b>não é
 * cacheado</b> (senão a recuperação da API demoraria 24h para aparecer) e uma resposta
 * vazia <b>não sobrescreve</b> a lista boa.</p>
 */
class WorldListIntegrationTest extends IntegrationTestBase {

    @MockBean TibiaDataClient tibiaDataClient;

    @Autowired WorldService worldService;
    @Autowired WorldRepository worldRepository;
    @Autowired CacheManager cacheManager;

    @BeforeEach
    void limparEstado() {
        // O cache é um bean singleton do contexto, compartilhado entre classes de teste:
        // sem limpar, a lista de um teste responde pelo seguinte.
        var cache = cacheManager.getCache(CacheConfig.WORLDS_CACHE);
        if (cache != null) {
            cache.clear();
        }
        worldRepository.deleteAll();
    }

    @Test
    void aPrimeiraConsultaGuardaAListaNoBanco() {
        quandoATibiaDataResponder("Antica", "Secura", "Bona");

        assertThat(worldService.listWorlds()).containsExactly("Antica", "Secura", "Bona");

        // O chão para o próximo restart: sem esta gravação, a lista existia só em memória.
        assertThat(worldRepository.findAllByOrderByNameAsc())
                .extracting(World::getName)
                .containsExactly("Antica", "Bona", "Secura");
    }

    @Test
    void aTibiaDataForaDoArDevolveAUltimaListaConhecida() {
        quandoATibiaDataResponder("Antica", "Secura");
        worldService.listWorlds();       // esquenta o cache e grava no banco
        limparCache();                   // simula o restart: memória fria, banco cheio
        quandoATibiaDataFalhar();

        // Era 503 aqui — e o filtro da home abria vazio.
        assertThat(worldService.listWorlds()).containsExactly("Antica", "Secura");
    }

    @Test
    void semListaNenhumaOErroSobe() {
        quandoATibiaDataFalhar();

        // Primeiro boot da vida com a API fora: não há o que servir, e responder `[]` com
        // 200 diria "o Tibia não tem mundos" — mentira pior que o 503.
        assertThatThrownBy(worldService::listWorlds)
                .isInstanceOf(ExternalServiceException.class);
    }

    @Test
    void falhaDeConexaoTambemVira503_eNao500() {
        // Conexão recusada sobe como WebClientRequestException, que **não** tem handler:
        // sem o embrulho, o endpoint responde 500 "Erro interno inesperado" — mentindo
        // sobre a culpa e envenenando o alerta de taxa de 5xx (mesmo argumento do S11).
        // Medido em produção local ao validar este item.
        when(tibiaDataClient.fetchWorlds()).thenReturn(Mono.error(
                new IllegalStateException("Connection refused: localhost/127.0.0.1:9")));

        assertThatThrownBy(worldService::listWorlds)
                .isInstanceOf(ExternalServiceException.class)
                .hasRootCauseMessage("Connection refused: localhost/127.0.0.1:9");
    }

    @Test
    void oFallbackNaoEhCacheado() {
        quandoATibiaDataResponder("Antica");
        worldService.listWorlds();
        limparCache();
        quandoATibiaDataFalhar();
        assertThat(worldService.listWorlds()).containsExactly("Antica"); // serviu o banco

        // A TibiaData voltou — e com um mundo novo.
        quandoATibiaDataResponder("Antica", "Nova");

        // Se o fallback tivesse sido cacheado, a lista nova só apareceria 24h depois.
        assertThat(worldService.listWorlds()).containsExactly("Antica", "Nova");
    }

    @Test
    void aRespostaVaziaNaoApagaAListaBoa() {
        quandoATibiaDataResponder("Antica", "Secura");
        worldService.listWorlds();
        limparCache();
        when(tibiaDataClient.fetchWorlds()).thenReturn(Mono.just(List.of()));

        // Lista vazia é tão inútil quanto nenhuma resposta: cai no fallback em vez de
        // gravar o vazio por cima do que funcionava.
        assertThat(worldService.listWorlds()).containsExactly("Antica", "Secura");
        assertThat(worldRepository.count()).isEqualTo(2);
    }

    @Test
    void mundoQueFechouDesapareceDaLista() {
        quandoATibiaDataResponder("Antica", "Fechado", "Secura");
        worldService.listWorlds();
        limparCache();

        quandoATibiaDataResponder("Antica", "Secura");
        assertThat(worldService.listWorlds()).containsExactly("Antica", "Secura");

        // O Tibia funde e encerra mundos. Uma lista que só cresce acumularia mundos mortos
        // no filtro para sempre.
        assertThat(worldRepository.findAllByOrderByNameAsc())
                .extracting(World::getName)
                .containsExactly("Antica", "Secura");
    }

    @Test
    void oCacheEvitaUmaSegundaChamadaAApiExterna() {
        quandoATibiaDataResponder("Antica");

        worldService.listWorlds();
        worldService.listWorlds();

        // O motivo de o cache existir: a home é a página mais visitada do site.
        verify(tibiaDataClient, times(1)).fetchWorlds();
    }

    @Test
    void listaIgualNaoDizQueGravouNada() {
        quandoATibiaDataResponder("Antica", "Secura");
        worldService.listWorlds();
        var antes = worldRepository.findAllByOrderByNameAsc().getFirst().getRefreshedAt();
        limparCache();
        ListAppender<ILoggingEvent> linhas = escutar(WorldServiceImpl.class);

        worldService.listWorlds(); // mesma lista

        // `refreshed_at` continua dizendo desde quando o mundo existe na lista…
        assertThat(worldRepository.findAllByOrderByNameAsc().getFirst().getRefreshedAt())
                .isEqualTo(antes);
        // …e o log **não** anuncia gravação: `worlds.persisted added=0 removed=0` a cada
        // vez que o cache esfria é a linha que ensina a ignorar o log inteiro.
        assertThat(linhas.list).noneSatisfy(evento ->
                assertThat(evento.getMessage()).startsWith("worlds.persisted"));
        pararDeEscutar(WorldServiceImpl.class, linhas);
    }

    @Test
    void mudancaNaListaEhAnunciadaNoLog() {
        quandoATibiaDataResponder("Antica");
        worldService.listWorlds();
        limparCache();
        ListAppender<ILoggingEvent> linhas = escutar(WorldServiceImpl.class);

        quandoATibiaDataResponder("Antica", "Nova");
        worldService.listWorlds();

        // O contrário do teste acima: quando muda, a linha existe — é o que se procura
        // para responder "quando a lista mudou?".
        assertThat(linhas.list)
                .filteredOn(e -> e.getMessage().startsWith("worlds.persisted"))
                .hasSize(1);
        pararDeEscutar(WorldServiceImpl.class, linhas);
    }

    // ----- Helpers -----

    private void quandoATibiaDataResponder(String... worlds) {
        when(tibiaDataClient.fetchWorlds()).thenReturn(Mono.just(List.of(worlds)));
    }

    private void quandoATibiaDataFalhar() {
        when(tibiaDataClient.fetchWorlds())
                .thenReturn(Mono.error(new ExternalServiceException("TibiaData fora do ar")));
    }

    /** Passa a guardar as linhas de log da classe, para afirmar sobre elas. */
    private ListAppender<ILoggingEvent> escutar(Class<?> classe) {
        ListAppender<ILoggingEvent> linhas = new ListAppender<>();
        linhas.start();
        ((Logger) LoggerFactory.getLogger(classe)).addAppender(linhas);
        return linhas;
    }

    private void pararDeEscutar(Class<?> classe, ListAppender<ILoggingEvent> linhas) {
        ((Logger) LoggerFactory.getLogger(classe)).detachAppender(linhas);
    }

    private void limparCache() {
        var cache = cacheManager.getCache(CacheConfig.WORLDS_CACHE);
        if (cache != null) {
            cache.clear();
        }
    }
}
