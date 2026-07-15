package com.exivamoeres.controller;

import com.exivamoeres.dto.chat.ChatMessageRequest;
import com.exivamoeres.dto.chat.ChatMessageResponse;
import com.exivamoeres.security.AuthenticatedUser;
import com.exivamoeres.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST do chat. O envio também é exposto aqui (além do WebSocket) para
 * simplificar o cliente: a resposta persiste E faz broadcast no tópico STOMP,
 * então quem envia por REST aparece em tempo real para quem está inscrito.
 * O histórico inicial é carregado por REST; o tempo real, via /ws.
 */
@RestController
@RequestMapping("/api/lists/{listId}/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping
    public Page<ChatMessageResponse> history(@AuthenticationPrincipal AuthenticatedUser user,
                                             @PathVariable Long listId,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "30") int size) {
        return chatService.getHistory(user.id(), listId, PageRequest.of(page, Math.min(size, 100)));
    }

    @PostMapping
    public ChatMessageResponse send(@AuthenticationPrincipal AuthenticatedUser user,
                                    @PathVariable Long listId,
                                    @Valid @RequestBody ChatMessageRequest request) {
        return chatService.sendMessage(user.id(), listId, request.characterId(), request.content());
    }
}
