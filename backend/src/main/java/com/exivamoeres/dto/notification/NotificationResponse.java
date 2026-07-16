package com.exivamoeres.dto.notification;

import com.exivamoeres.domain.Notification;
import com.exivamoeres.domain.NotificationType;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        NotificationType type,
        Long listId,
        String listName,
        boolean read,
        Instant createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getType(),
                n.getList() != null ? n.getList().getId() : null,
                n.getListName(),
                n.isRead(),
                n.getCreatedAt());
    }
}
