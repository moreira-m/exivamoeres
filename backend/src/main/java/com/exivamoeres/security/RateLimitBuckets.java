package com.exivamoeres.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Onde moram os baldes de rate limit — um por chave, <b>com teto e vencimento</b>.
 *
 * Existe porque os três limitadores (RateLimitFilter por IP, UserRateLimiter e
 * ChatRateLimiter por usuário) guardavam os baldes num {@code ConcurrentHashMap}
 * que <b>nunca</b> perdia entrada: cada IP e cada usuário que passasse uma vez
 * ficava na memória até o processo reiniciar. No limitador por IP isso era pior
 * que um vazamento lento — a chave vem de um endereço que o cliente influencia,
 * então bastava variar a chave para o mapa crescer sem limite.
 *
 * <p><b>Por que expirar não afrouxa o limite:</b> o vencimento é por
 * <i>último acesso</i> e é maior que a maior janela de recarga em uso (1 hora).
 * Um balde só é descartado depois de tempo suficiente para já ter voltado ao
 * cheio — descartá-lo devolve exatamente o que o refill devolveria.
 *
 * <p>Continua em memória: com mais de uma instância, cada uma tem o seu balde
 * (mesma limitação de antes — resolver isso é trocar pelo backend distribuído do
 * Bucket4j, não mexer aqui).
 */
@Component
public class RateLimitBuckets {

    /**
     * Teto de chaves vivas. O Caffeine desempata por frequência de uso, não por
     * ordem de chegada: uma enxurrada de chaves novas não expulsa o balde de quem
     * está batendo no limite, que é justamente o que precisa sobreviver.
     */
    private static final int MAXIMO_DE_CHAVES = 50_000;

    /** Folga sobre a maior janela de recarga (1 hora) — ver o Javadoc da classe. */
    private static final Duration OCIOSIDADE_ATE_DESCARTAR = Duration.ofHours(2);

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(MAXIMO_DE_CHAVES)
            .expireAfterAccess(OCIOSIDADE_ATE_DESCARTAR)
            .build();

    /** O balde da chave, criado com este limite na primeira vez que a chave aparece. */
    public Bucket forKey(String key, Bandwidth limit) {
        return buckets.get(key, ignored -> Bucket.builder().addLimit(limit).build());
    }
}
