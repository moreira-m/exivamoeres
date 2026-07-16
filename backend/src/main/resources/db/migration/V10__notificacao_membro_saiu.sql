-- Novo tipo de notificação: MEMBER_LEFT (dono é avisado quando um membro sai).
ALTER TABLE notifications DROP CONSTRAINT chk_notifications_type;
ALTER TABLE notifications
    ADD CONSTRAINT chk_notifications_type CHECK (type IN (
        'JOIN_REQUEST_RECEIVED', 'JOIN_REQUEST_APPROVED', 'JOIN_REQUEST_REJECTED',
        'KICKED_FROM_TEAM', 'TEAM_DELETED', 'MEMBER_LEFT'));
