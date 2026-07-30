-- O P18 (V16) avisa quem tem pedido PENDENTE quando o level mínimo sobe acima do
-- personagem. A composição por vocação (P3, V17) criou um segundo jeito de o pedido
-- virar inaprovável, e este não avisava ninguém: o dono reconfigura as vagas, a
-- vocação do pedido deixa de ter lugar, e quem pediu continua esperando por uma
-- aprovação que vai ser recusada com uma mensagem que só o dono lê.
--
-- Tipo próprio, e não reuso do JOIN_REQUEST_AT_RISK, porque a frase é outra e é a
-- frase que importa: "o time subiu o level mínimo" manda a pessoa subir de level;
-- "a composição não tem vaga para a sua vocação" manda escolher outro personagem ou
-- outro time. Aviso com o motivo errado é pior que aviso nenhum — leva a pessoa a
-- tentar consertar o que não está quebrado.
ALTER TABLE notifications DROP CONSTRAINT chk_notifications_type;
ALTER TABLE notifications
    ADD CONSTRAINT chk_notifications_type CHECK (type IN (
        'JOIN_REQUEST_RECEIVED', 'JOIN_REQUEST_APPROVED', 'JOIN_REQUEST_REJECTED',
        'KICKED_FROM_TEAM', 'TEAM_DELETED', 'MEMBER_LEFT',
        'TEAM_SCHEDULE_CHANGED', 'TEAM_MINIMUM_LEVEL_CHANGED',
        'JOIN_REQUEST_AT_RISK', 'JOIN_REQUEST_COMPOSITION_MISMATCH'));
