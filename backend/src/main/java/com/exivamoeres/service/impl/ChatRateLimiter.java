package com.exivamoeres.service.impl;

import com.exivamoeres.config.ChatProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limit de mensagens de chat por usuário (mensagens/minuto, ver
 * ChatProperties). Buckets em memória — mesma limitação single-instance do
 * RateLimitFilter de auth, documentada em docs/proxima-sessao.md.
 */
@Component
public class ChatRateLimiter {

    private final Map<Long, Bucket> buckets = new ConcurrentHashMap<>();
    private final int messagesPerMinute;

    public ChatRateLimiter(ChatProperties chatProperties) {
        this.messagesPerMinute = chatProperties.messagesPerMinute();
    }

    public boolean tryConsume(Long userId) {
        return buckets.computeIfAbsent(userId, id -> newBucket()).tryConsume(1);
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(messagesPerMinute)
                        .refillGreedy(messagesPerMinute, Duration.ofMinutes(1))
                        .build())
                .build();
    }
}
