-- Notificações por usuário (item 7). Aditiva.
CREATE TABLE notifications (
    id           BIGSERIAL PRIMARY KEY,
    recipient_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type         VARCHAR(40) NOT NULL,
    list_id      BIGINT REFERENCES hunting_lists (id) ON DELETE SET NULL,
    list_name    VARCHAR(100),
    read         BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_notifications_type CHECK (type IN (
        'JOIN_REQUEST_RECEIVED', 'JOIN_REQUEST_APPROVED', 'JOIN_REQUEST_REJECTED',
        'KICKED_FROM_TEAM', 'TEAM_DELETED'))
);

-- Lista de notificações do usuário, das mais recentes primeiro (feed paginado).
CREATE INDEX ix_notifications_recipient_created
    ON notifications (recipient_id, created_at DESC);

-- Contagem rápida de não-lidas (badge) — índice parcial só do que interessa.
CREATE INDEX ix_notifications_unread
    ON notifications (recipient_id)
    WHERE read = FALSE;
