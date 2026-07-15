package com.exivamoeres.service;

import com.exivamoeres.dto.chat.ChatMessageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Chat por time. Cada mensagem é enviada "como" um personagem (ChatMessage é
 * vinculada a Character, não só a User). Só membros ativos e aprovados do time
 * podem enviar/ler. O envio tem rate limit por usuário (ver ChatProperties).
 */
public interface ChatService {

    /** Persiste a mensagem e faz broadcast em /topic/lists/{listId}/chat. */
    ChatMessageResponse sendMessage(Long userId, Long listId, Long characterId, String content);

    /** Histórico paginado (mais recentes primeiro). */
    Page<ChatMessageResponse> getHistory(Long userId, Long listId, Pageable pageable);
}
