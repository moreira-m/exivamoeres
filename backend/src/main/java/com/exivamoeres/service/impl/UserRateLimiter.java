package com.exivamoeres.service.impl;

import com.exivamoeres.config.RateLimitProperties;
import com.exivamoeres.domain.exception.TooManyRequestsException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limit por <b>usuário</b> das ações caras — as que o rate limit por IP do
 * RateLimitFilter não alcança, porque acontecem depois do login:
 *
 * <ul>
 *   <li><b>criar time</b>: o teto de times ATIVOS do plano não impede criar,
 *       encerrar e criar de novo em loop, enchendo a busca pública;</li>
 *   <li><b>consultar a TibiaData</b> (claim e entrada em time): sem limite, o
 *       site vira amplificador de tráfego contra uma API pública e gratuita;</li>
 *   <li><b>editar o time</b>: mudar horário ou level mínimo <b>notifica todos os
 *       membros aprovados</b>. É a única ação do produto em que um usuário gera
 *       escrita persistente na caixa de outros — sem teto, alternar o horário em
 *       loop enche o sino de quatro pessoas.</li>
 * </ul>
 *
 * Buckets em memória — mesma limitação single-instance do RateLimitFilter e do
 * ChatRateLimiter.
 */
@Component
@Slf4j
public class UserRateLimiter {

    private static final String TEAM_CREATION = "team-creation";
    private static final String TIBIADATA = "tibiadata";
    private static final String TEAM_UPDATE = "team-update";

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final RateLimitProperties properties;

    public UserRateLimiter(RateLimitProperties properties) {
        this.properties = properties;
    }

    /** Lança 429 se o usuário já criou times demais na última hora. */
    public void checkTeamCreation(Long userId) {
        consume(TEAM_CREATION, userId, properties.teamCreationPerHour(),
                "Você criou muitos times em pouco tempo. Aguarde um pouco para criar outro.");
    }

    /** Lança 429 se o usuário já disparou consultas demais à TibiaData na última hora. */
    public void checkTibiaDataLookup(Long userId) {
        consume(TIBIADATA, userId, properties.tibiadataPerHour(),
                "Muitas consultas ao Tibia.com em pouco tempo. Aguarde alguns minutos.");
    }

    /**
     * Lança 429 se o usuário já editou times demais na última hora.
     *
     * Limita a edição <b>inteira</b>, não só as que notificam: é mais simples de
     * explicar para quem recebe o erro e protege a tabela de notificações do
     * mesmo jeito — quem alterna o horário em loop passa pelo mesmo balde de
     * quem só arruma a descrição.
     */
    public void checkTeamUpdate(Long userId) {
        consume(TEAM_UPDATE, userId, properties.teamUpdatePerHour(),
                "Você editou este time muitas vezes em pouco tempo. Aguarde um pouco para ajustar de novo.");
    }

    private void consume(String action, Long userId, int perHour, String message) {
        Bucket bucket = buckets.computeIfAbsent(action + ":" + userId,
                key -> newBucket(perHour));
        if (!bucket.tryConsume(1)) {
            log.warn("ratelimit.user.exceeded action={} userId={} limit={}/h", action, userId, perHour);
            throw new TooManyRequestsException(message);
        }
    }

    private Bucket newBucket(int perHour) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(perHour)
                        .refillGreedy(perHour, Duration.ofHours(1))
                        .build())
                .build();
    }
}
