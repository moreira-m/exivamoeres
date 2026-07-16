package com.exivamoeres.service.impl;

import com.exivamoeres.domain.HuntingList;
import com.exivamoeres.domain.Notification;
import com.exivamoeres.domain.NotificationType;
import com.exivamoeres.domain.exception.ResourceNotFoundException;
import com.exivamoeres.dto.notification.NotificationResponse;
import com.exivamoeres.repository.NotificationRepository;
import com.exivamoeres.repository.UserRepository;
import com.exivamoeres.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationServiceImpl(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public void notifyJoinRequestReceived(Long ownerId, HuntingList list) {
        create(ownerId, NotificationType.JOIN_REQUEST_RECEIVED, list);
    }

    @Override
    @Transactional
    public void notifyJoinRequestApproved(Long requesterId, HuntingList list) {
        create(requesterId, NotificationType.JOIN_REQUEST_APPROVED, list);
    }

    @Override
    @Transactional
    public void notifyJoinRequestRejected(Long requesterId, HuntingList list) {
        create(requesterId, NotificationType.JOIN_REQUEST_REJECTED, list);
    }

    @Override
    @Transactional
    public void notifyKicked(Long kickedUserId, HuntingList list) {
        create(kickedUserId, NotificationType.KICKED_FROM_TEAM, list);
    }

    @Override
    @Transactional
    public void notifyTeamDeleted(Long memberId, HuntingList list) {
        create(memberId, NotificationType.TEAM_DELETED, list);
    }

    @Override
    @Transactional
    public void notifyMemberLeft(Long ownerId, HuntingList list) {
        create(ownerId, NotificationType.MEMBER_LEFT, list);
    }

    private void create(Long recipientId, NotificationType type, HuntingList list) {
        Notification notification = new Notification();
        // getReferenceById evita carregar o usuário só para setar a FK.
        notification.setRecipient(userRepository.getReferenceById(recipientId));
        notification.setType(type);
        notification.setList(list);
        notification.setListName(list != null ? list.getName() : null);
        notificationRepository.save(notification);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(Long userId, Pageable pageable) {
        return notificationRepository
                .findAllByRecipientIdOrderByCreatedAtDesc(userId, pageable)
                .map(NotificationResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(Long userId) {
        return notificationRepository.countByRecipientIdAndReadFalse(userId);
    }

    @Override
    @Transactional
    public void markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notificação não encontrada"));
        // Só o dono da notificação a marca; não vaza existência de outras.
        if (!notification.getRecipient().getId().equals(userId)) {
            throw new ResourceNotFoundException("Notificação não encontrada");
        }
        notification.setRead(true);
    }

    @Override
    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllReadByRecipientId(userId);
    }
}
