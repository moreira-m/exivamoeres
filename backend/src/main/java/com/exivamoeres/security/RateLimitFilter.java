package com.exivamoeres.security;

import com.exivamoeres.config.RateLimitProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting por IP nos endpoints que criam identidade:
 *
 * <ul>
 *   <li><b>login/registro</b> (10/min) — força bruta e enumeração de contas;</li>
 *   <li><b>conta anônima</b> (3/hora) — criar conta sem email, sem senha e sem
 *       captcha é a porta de entrada mais barata para encher a busca pública de
 *       times de mentira. Para um humano é uma ação rara; daí o limite apertado.</li>
 * </ul>
 *
 * O que uma conta já criada pode fazer é limitado por usuário, em
 * {@link com.exivamoeres.service.impl.UserRateLimiter}.
 *
 * NOTA: buckets em memória — suficiente para uma instância. Se o backend
 * escalar horizontalmente, migrar para o backend distribuído do Bucket4j.
 */
@Component
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> CREDENTIAL_PATHS =
            Set.of("/api/auth/login", "/api/auth/register");
    private static final String ANONYMOUS_PATH = "/api/auth/anonymous";

    /** Limite aplicado a um caminho, com a mensagem que o usuário vê ao estourar. */
    private record Policy(Bandwidth bandwidth, String message) {
    }

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Policy credentialPolicy;
    private final Policy anonymousPolicy;

    public RateLimitFilter(RateLimitProperties properties) {
        this.credentialPolicy = new Policy(
                perDuration(properties.authPerMinute(), Duration.ofMinutes(1)),
                "Muitas tentativas. Aguarde um minuto.");
        this.anonymousPolicy = new Policy(
                perDuration(properties.anonymousPerHour(), Duration.ofHours(1)),
                "Muitas contas criadas a partir deste endereço. Tente novamente mais tarde.");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        Policy policy = policyFor(path);
        if (policy == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = clientIp(request);
        // A chave inclui o caminho: estourar o limite de login não pode consumir
        // o de conta anônima (e vice-versa) — são limites diferentes.
        Bucket bucket = buckets.computeIfAbsent(path + "|" + ip,
                key -> Bucket.builder().addLimit(policy.bandwidth()).build());
        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            log.warn("ratelimit.ip.exceeded path={} ip={}", path, ip);
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"status\":429,\"message\":\"" + policy.message() + "\"}");
        }
    }

    private Policy policyFor(String path) {
        if (CREDENTIAL_PATHS.contains(path)) {
            return credentialPolicy;
        }
        if (ANONYMOUS_PATH.equals(path)) {
            return anonymousPolicy;
        }
        return null;
    }

    private Bandwidth perDuration(int capacity, Duration period) {
        return Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, period)
                .build();
    }

    private String clientIp(HttpServletRequest request) {
        // Atrás de proxy (Railway) o IP real chega no X-Forwarded-For.
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }
}
