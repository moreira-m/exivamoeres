package com.exivamoeres.service.impl;

import com.exivamoeres.client.TibiaDataClient;
import com.exivamoeres.config.CacheConfig;
import com.exivamoeres.domain.World;
import com.exivamoeres.domain.exception.ExternalServiceException;
import com.exivamoeres.repository.WorldRepository;
import com.exivamoeres.service.WorldService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A lista de mundos válidos, com <b>três camadas</b> — e a terceira é a novidade do item
 * S13:
 *
 * <ol>
 *   <li><b>cache em memória</b> (24h, {@code app.cache.worlds-ttl}) — evita martelar a
 *       TibiaData a cada visitante;</li>
 *   <li><b>TibiaData</b> — a fonte da verdade, consultada quando o cache está frio;</li>
 *   <li><b>banco</b> — a última lista boa, gravada a cada resposta bem-sucedida.</li>
 * </ol>
 *
 * <p><b>O problema que a terceira camada resolve.</b> O cache é em memória, então "frio"
 * acontece a cada restart: deploy, reinício do Railway, crash. Se a TibiaData estivesse
 * indisponível nesse instante, {@code /api/worlds} respondia <b>503</b> e o filtro de
 * mundos da home — o primeiro controle que todo visitante vê — abria vazio. Agora a falha
 * degrada para "a lista de ontem", que é praticamente igual (a lista do Tibia muda algumas
 * vezes por ano).</p>
 *
 * <p><b>Por que o cache é manual e não {@code @Cacheable}.</b> A anotação guardaria também
 * a resposta de <b>fallback</b>, e por 24 horas: um boot durante uma queda da TibiaData
 * prenderia o site na lista antiga por um dia inteiro, mesmo depois de a API voltar. Aqui,
 * só o caminho bem-sucedido é cacheado — a falha serve o banco e deixa a próxima
 * requisição tentar de novo.</p>
 */
@Service
@Slf4j
public class WorldServiceImpl implements WorldService {

    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(20);

    /** Chave única: o cache guarda uma lista só (`maximumSize(1)` no CacheConfig). */
    private static final String CACHE_KEY = "all";

    private final TibiaDataClient tibiaDataClient;
    private final WorldRepository worldRepository;
    private final CacheManager cacheManager;

    public WorldServiceImpl(TibiaDataClient tibiaDataClient,
                            WorldRepository worldRepository,
                            CacheManager cacheManager) {
        this.tibiaDataClient = tibiaDataClient;
        this.worldRepository = worldRepository;
        this.cacheManager = cacheManager;
    }

    @Override
    @Transactional
    public List<String> listWorlds() {
        List<String> cacheados = doCache();
        if (cacheados != null) {
            return cacheados;
        }
        try {
            List<String> daTibiaData = tibiaDataClient.fetchWorlds().block(FETCH_TIMEOUT);
            if (daTibiaData == null || daTibiaData.isEmpty()) {
                // Resposta vazia é tão inútil quanto nenhuma resposta — e **não** pode
                // sobrescrever a lista boa que está no banco.
                throw new ExternalServiceException("TibiaData não respondeu");
            }
            guardar(daTibiaData);
            cache().put(CACHE_KEY, daTibiaData);
            return daTibiaData;
        } catch (RuntimeException falha) {
            return ultimaListaConhecida(falha);
        }
    }

    /**
     * A lista do banco quando a TibiaData falha. Se o banco também estiver vazio (primeiro
     * boot da vida com a API fora), o erro <b>sobe como 503</b>: não há lista nenhuma para
     * servir, e devolver `[]` com 200 diria "o Tibia não tem mundos" — mentira pior que o
     * erro.
     *
     * <p><b>Sempre {@link ExternalServiceException}</b>, mesmo quando a causa é outra: sem
     * este embrulho, uma conexão recusada sobe como {@code WebClientRequestException}, que
     * não tem handler e vira <b>500 "Erro interno inesperado"</b> — medido em produção
     * local ao validar este item. Além de mentir sobre a culpa (o terceiro caiu, não nós),
     * um 500 aqui envenenaria o alerta de taxa de 5xx do
     * {@code ops/observabilidade/alertas.yml}, que é o mesmo argumento do S11.</p>
     */
    private List<String> ultimaListaConhecida(RuntimeException falha) {
        List<String> doBanco = nomesGravados();
        if (doBanco.isEmpty()) {
            log.warn("worlds.unavailable error={} fallback=none", falha.toString());
            throw falha instanceof ExternalServiceException
                    ? falha
                    : new ExternalServiceException("TibiaData indisponível e sem lista conhecida", falha);
        }
        log.warn("worlds.stale error={} fallback={}", falha.toString(), doBanco.size());
        return doBanco;
    }

    /**
     * Alinha a lista gravada com a que a TibiaData acabou de devolver: insere o que
     * apareceu, apaga o que saiu, e <b>não toca</b> no que continua igual.
     *
     * <p><b>Apagar o que saiu é a parte obrigatória</b>: o Tibia funde e encerra mundos, e
     * uma lista que só cresce acumularia mundos mortos no filtro para sempre.</p>
     *
     * <p><b>Diferença, e não "apaga tudo e regrava"</b>, por dois motivos. O primeiro é
     * técnico e a suíte cobrou na primeira versão: com {@code @Id} atribuído (o nome), o
     * {@code save} de uma linha recém-apagada na <b>mesma</b> transação vira um
     * {@code merge} e estoura {@code ObjectOptimisticLockingFailureException}. O segundo é
     * de leitura: assim o {@code refreshed_at} de cada mundo continua dizendo <b>desde
     * quando ele existe na lista</b>, que é a informação útil numa investigação.</p>
     */
    private void guardar(List<String> worlds) {
        Set<String> novos = Set.copyOf(worlds);
        Set<String> atuais = Set.copyOf(nomesGravados());

        List<World> entraram = novos.stream()
                .filter(nome -> !atuais.contains(nome))
                .map(World::new)
                .toList();
        List<String> sairam = atuais.stream().filter(nome -> !novos.contains(nome)).toList();
        if (entraram.isEmpty() && sairam.isEmpty()) {
            return;
        }
        worldRepository.saveAll(entraram);
        worldRepository.deleteAllById(sairam);
        log.info("worlds.persisted total={} added={} removed={}",
                worlds.size(), entraram.size(), sairam.size());
    }

    private List<String> nomesGravados() {
        return worldRepository.findAllByOrderByNameAsc().stream()
                .map(World::getName)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private List<String> doCache() {
        Cache.ValueWrapper valor = cache().get(CACHE_KEY);
        return valor == null ? null : (List<String>) valor.get();
    }

    private Cache cache() {
        Cache cache = cacheManager.getCache(CacheConfig.WORLDS_CACHE);
        if (cache == null) {
            throw new IllegalStateException("cache de worlds não registrado (ver CacheConfig)");
        }
        return cache;
    }
}
