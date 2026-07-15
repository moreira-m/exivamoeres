package com.exivamoeres.config;

import com.exivamoeres.security.StompAuthChannelInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Chat por time via STOMP sobre WebSocket.
 *
 * - endpoint de handshake: /ws (origem restrita ao frontend, igual ao CORS);
 * - broker simples em memória: destinos /topic/lists/{id}/chat;
 * - autenticação: JWT no header STOMP CONNECT, validado pelo
 *   StompAuthChannelInterceptor (mesma JwtService do REST).
 *
 * O broker simples é single-instance; se o backend escalar, trocar por um
 * broker externo (RabbitMQ/ActiveMQ) — documentado em docs/proxima-sessao.md.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor authChannelInterceptor;
    private final String allowedOrigin;

    public WebSocketConfig(StompAuthChannelInterceptor authChannelInterceptor,
                           @Value("${app.cors.allowed-origin}") String allowedOrigin) {
        this.authChannelInterceptor = authChannelInterceptor;
        this.allowedOrigin = allowedOrigin;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(allowedOrigin);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authChannelInterceptor);
    }
}
