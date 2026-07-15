package com.exivamoeres.dto.chat;

import com.exivamoeres.domain.ChatMessage;

import java.time.Instant;

public record ChatMessageResponse(
        Long id,
        Long listId,
        Long senderId,
        String senderDisplayName,
        Long characterId,
        String characterName,
        String content,
        Instant sentAt
) {
    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getList().getId(),
                message.getSender().getId(),
                message.getSender().getDisplayName(),
                message.getCharacter().getId(),
                message.getCharacter().getName(),
                message.getContent(),
                message.getSentAt());
    }
}
