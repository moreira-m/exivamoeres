-- Quem pede para entrar num time passa a ver o pedido (e a poder desistir dele).
-- Cancelar precisa de um status próprio: 'REJECTED' significa "o dono não quis" —
-- overloadar isso faria o histórico do usuário mentir sobre quem decidiu o quê
-- (a mesma razão pela qual a inelegibilidade na aprovação mantém 'PENDING', V14).
--
-- Aditiva e inerte para o código existente: todas as consultas filtram status
-- explicitamente ('PENDING', 'APPROVED'), então nenhuma passa a incluir 'CANCELLED'
-- por acidente.
ALTER TABLE list_memberships DROP CONSTRAINT chk_list_memberships_status;
ALTER TABLE list_memberships
    ADD CONSTRAINT chk_list_memberships_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'));

-- A tela "meus pedidos" busca por USUÁRIO (o índice parcial da V5 é por lista,
-- para o dono varrer os pendentes do time dele). Índice parcial no mesmo espírito:
-- só o que a tela realmente procura.
CREATE INDEX ix_list_memberships_user_pending
    ON list_memberships (user_id)
    WHERE status = 'PENDING' AND active = TRUE;
