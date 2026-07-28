-- Editar o time (V13 + PATCH) era silencioso: quem entrou porque o horário era
-- "Seg–Sex 20h" não sabia quando ele virava "Sáb 3h". Dois tipos novos avisam os
-- membros aprovados quando muda algo que altera a decisão de participar.
--
-- Dois tipos em vez de um "TEAM_UPDATED" genérico de propósito: a notificação só
-- serve se disser O QUE mudou. "O time foi atualizado" é a notificação que se
-- aprende a ignorar.
--
-- Descrição e contato NÃO geram aviso: corrigir uma vírgula no texto não pode
-- virar notificação para todo o time.
ALTER TABLE notifications DROP CONSTRAINT chk_notifications_type;
ALTER TABLE notifications
    ADD CONSTRAINT chk_notifications_type CHECK (type IN (
        'JOIN_REQUEST_RECEIVED', 'JOIN_REQUEST_APPROVED', 'JOIN_REQUEST_REJECTED',
        'KICKED_FROM_TEAM', 'TEAM_DELETED', 'MEMBER_LEFT',
        'TEAM_SCHEDULE_CHANGED', 'TEAM_MINIMUM_LEVEL_CHANGED'));
