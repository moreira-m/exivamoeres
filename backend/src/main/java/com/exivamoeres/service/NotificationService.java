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

    /**
     * Avisa um membro aprovado que o **horário da caçada** mudou.
     * Só o horário e o level mínimo geram aviso — ver
     * {@code HuntingListServiceImpl#notifyRelevantChanges}.
     */
    void notifyTeamScheduleChanged(Long memberId, HuntingList list);

    /** Avisa um membro aprovado que o **level mínimo** do time mudou. */
    void notifyTeamMinimumLevelChanged(Long memberId, HuntingList list);

    /**
     * Avisa quem tem pedido **pendente** que ele passou a não caber no requisito
     * do time (o level mínimo subiu acima do personagem do pedido).
     */
    void notifyJoinRequestAtRisk(Long requesterId, HuntingList list);

    /**
     * Avisa quem pediu que a composição nova do time não tem vaga para a vocação do
     * personagem do pedido (P21).
     */
    void notifyJoinRequestCompositionMismatch(Long requesterId, HuntingList list);

    /**
     * Avisa quem pediu que o pedido <b>voltou a caber</b>: o motivo que impedia a
     * aprovação sumiu (P19).
     */
    void notifyJoinRequestFitsAgain(Long requesterId, HuntingList list);

    Page<NotificationResponse> list(Long userId, Pageable pageable);

    long countUnread(Long userId);

    /** Marca uma notificação específica do usuário como lida. */
    void markRead(Long userId, Long notificationId);

    /** Marca todas as notificações do usuário como lidas. */
    void markAllRead(Long userId);
}
