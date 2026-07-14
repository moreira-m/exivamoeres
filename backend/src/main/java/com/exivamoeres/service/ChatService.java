package com.exivamoeres.service;

/**
 * ESQUELETO PARA A SESSÃO 2 — chat por lista via WebSocket (STOMP).
 * Ver o plano em config/WebSocketConfig. A entidade ChatMessage e a
 * migration já existem; o histórico é paginado por sent_at DESC
 * (índice ix_chat_messages_list_sent).
 */
public interface ChatService {

    /**
     * Persiste e distribui uma mensagem para os membros ativos da lista.
     * TODO(sessão 2): implementar + broadcast em /topic/lists/{listId}/chat.
     */
    Object sendMessage(Long userId, Long listId, String content);

    /** TODO(sessão 2): histórico paginado (só para membros ativos). */
    Object getHistory(Long userId, Long listId, int page, int size);
}
