package com.exivamoeres.security;

import com.exivamoeres.domain.MembershipStatus;
import com.exivamoeres.repository.ListMembershipRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Segurança do canal STOMP:
 * - no CONNECT, exige "Authorization: Bearer <jwt>" válido (mesma JwtService
 *   do REST) e fixa o usuário autenticado na sessão;
 * - no SUBSCRIBE a /topic/lists/{id}/chat, exige que o usuário seja membro
 *   ativo e aprovado do time (senão poderia ler o chat de qualquer time).
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Pattern CHAT_TOPIC = Pattern.compile("^/topic/lists/(\\d+)/chat$");

    private final JwtService jwtService;
    private final ListMembershipRepository membershipRepository;

    public StompAuthChannelInterceptor(JwtService jwtService,
                                       ListMembershipRepository membershipRepository) {
        this.jwtService = jwtService;
        this.membershipRepository = membershipRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        switch (accessor.getCommand()) {
            case CONNECT -> authenticate(accessor);
            case SUBSCRIBE -> authorizeSubscription(accessor);
            default -> {
                // Demais frames não exigem verificação extra aqui.
            }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        AuthenticatedUser user = extractToken(accessor)
                .flatMap(jwtService::parse)
                .orElseThrow(() -> new IllegalArgumentException("Token JWT ausente ou inválido no CONNECT"));
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }
        Matcher matcher = CHAT_TOPIC.matcher(destination);
        if (!matcher.matches()) {
            return; // Só tópicos de chat de time são protegidos aqui.
        }
        Long listId = Long.valueOf(matcher.group(1));
        Long userId = currentUserId(accessor);
        boolean member = membershipRepository.existsByListIdAndUserIdAndActiveTrueAndStatus(
                listId, userId, MembershipStatus.APPROVED);
        if (!member) {
            throw new IllegalArgumentException("Você não participa deste time");
        }
    }

    private Long currentUserId(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof AuthenticatedUser user) {
            return user.id();
        }
        throw new IllegalArgumentException("Sessão não autenticada");
    }

    private Optional<String> extractToken(StompHeaderAccessor accessor) {
        List<String> headers = accessor.getNativeHeader("Authorization");
        if (headers == null || headers.isEmpty()) {
            return Optional.empty();
        }
        String header = headers.get(0);
        return header != null && header.startsWith(BEARER_PREFIX)
                ? Optional.of(header.substring(BEARER_PREFIX.length()))
                : Optional.empty();
    }
}
