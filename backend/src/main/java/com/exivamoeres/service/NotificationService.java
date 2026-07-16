package com.exivamoeres.service;

import com.exivamoeres.domain.HuntingList;
import com.exivamoeres.dto.notification.NotificationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Cria e consulta notificações. Os métodos "notify*" são chamados pelos
 * fluxos de time (pedido de entrada, aprovação, expulsão, encerramento) para
 * registrar o evento para o destinatário.
 */
public interface NotificationService {

    void notifyJoinRequestReceived(Long ownerId, HuntingList list);

    void notifyJoinRequestApproved(Long requesterId, HuntingList list);

    void notifyJoinRequestRejected(Long requesterId, HuntingList list);

    void notifyKicked(Long kickedUserId, HuntingList list);

    void notifyTeamDeleted(Long memberId, HuntingList list);

    /** Avisa o dono do time que um membro saiu. */
    void notifyMemberLeft(Long ownerId, HuntingList list);

    Page<NotificationResponse> list(Long userId, Pageable pageable);

    long countUnread(Long userId);

    /** Marca uma notificação específica do usuário como lida. */
    void markRead(Long userId, Long notificationId);

    /** Marca todas as notificações do usuário como lidas. */
    void markAllRead(Long userId);
}
