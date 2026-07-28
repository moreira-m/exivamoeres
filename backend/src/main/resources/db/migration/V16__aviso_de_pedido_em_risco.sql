-- O P16 avisa os membros APROVADOS quando o horário ou o level mínimo mudam. Quem
-- tem pedido PENDENTE não recebia nada — e é justamente quem mais precisa saber:
-- se o dono sobe o level mínimo acima do personagem dele, o pedido virou
-- inaprovável e ele só descobria se voltasse à aba "meus pedidos".
--
-- Tipo próprio e não reuso do TEAM_MINIMUM_LEVEL_CHANGED porque o destinatário é
-- outro: para o membro, "o level mínimo do time mudou"; para quem pediu, o que
-- importa é "SEU PEDIDO pode não ser aprovado". Mesma frase não serve para os dois.
ALTER TABLE notifications DROP CONSTRAINT chk_notifications_type;
ALTER TABLE notifications
    ADD CONSTRAINT chk_notifications_type CHECK (type IN (
        'JOIN_REQUEST_RECEIVED', 'JOIN_REQUEST_APPROVED', 'JOIN_REQUEST_REJECTED',
        'KICKED_FROM_TEAM', 'TEAM_DELETED', 'MEMBER_LEFT',
        'TEAM_SCHEDULE_CHANGED', 'TEAM_MINIMUM_LEVEL_CHANGED',
        'JOIN_REQUEST_AT_RISK'));
