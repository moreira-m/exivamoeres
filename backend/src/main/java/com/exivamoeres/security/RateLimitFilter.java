package com.exivamoeres.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting por IP nos endpoints de credencial (login/registro), mitigando
 * força bruta e enumeração de contas. 10 tentativas por minuto por IP.
 *
 * NOTA: buckets em memória — suficiente para uma instância. Se o backend
 * escalar horizontalmente, migrar para o backend distribuído do Bucket4j.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS =
            Set.of("/api/auth/login", "/api/auth/register");
    private static final int REQUESTS_PER_MINUTE = 10;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!LIMITED_PATHS.contains(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        Bucket bucket = buckets.computeIfAbsent(clientIp(request), ip -> newBucket());
        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"message\":\"Muitas tentativas. Aguarde um minuto.\"}");
        }
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(REQUESTS_PER_MINUTE)
                        .refillGreedy(REQUESTS_PER_MINUTE, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    private String clientIp(HttpServletRequest request) {
        // Atrás de proxy (Railway) o IP real chega no X-Forwarded-For.
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }
}
