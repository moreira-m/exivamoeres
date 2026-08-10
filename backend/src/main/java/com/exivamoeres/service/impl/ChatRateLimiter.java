package com.exivamoeres.service.impl;

import com.exivamoeres.config.ChatProperties;
import com.exivamoeres.security.RateLimitBuckets;
import io.github.bucket4j.Bandwidth;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Rate limit de mensagens de chat por usuário (mensagens/minuto, ver
 * ChatProperties). Buckets em {@link RateLimitBuckets} — mesma limitação
 * single-instance do RateLimitFilter de auth, documentada em docs/proxima-sessao.md.
 */
@Component
public class ChatRateLimiter {

    private final RateLimitBuckets buckets;
    private final Bandwidth limit;

    public ChatRateLimiter(ChatProperties chatProperties, RateLimitBuckets buckets) {
        this.buckets = buckets;
        this.limit = Bandwidth.builder()
                .capacity(chatProperties.messagesPerMinute())
                .refillGreedy(chatProperties.messagesPerMinute(), Duration.ofMinutes(1))
                .build();
    }

    public boolean tryConsume(Long userId) {
        // Prefixo na chave: os baldes de todos os limitadores dividem a mesma loja,
        // e "12" do chat não pode ser o mesmo balde de "12" de outra coisa.
        return buckets.forKey("chat:" + userId, limit).tryConsume(1);
    }
}
