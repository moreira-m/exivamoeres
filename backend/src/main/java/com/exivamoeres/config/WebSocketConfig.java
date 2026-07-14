package com.exivamoeres.config;

/**
 * Placeholder para a sessão 2: o chat das listas usará WebSocket (STOMP).
 *
 * Plano previsto:
 * - dependência spring-boot-starter-websocket no pom;
 * - @EnableWebSocketMessageBroker, endpoint /ws com allowed-origin do
 *   frontend (mesma env var FRONTEND_URL usada no CORS);
 * - broker simples /topic/lists/{listId}/chat;
 * - autenticação do handshake reaproveitando o JwtService
 *   (token via query param ou header, validado num HandshakeInterceptor).
 *
 * Mantido como classe vazia (e não config ativa) para não carregar
 * infraestrutura WebSocket antes de existir chat.
 */
public final class WebSocketConfig {

    private WebSocketConfig() {
    }
}
