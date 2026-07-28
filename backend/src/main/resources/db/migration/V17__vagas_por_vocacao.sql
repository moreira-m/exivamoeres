-- Composição do time por vocação: "faltam 1 EK e 1 ED" é o que se anuncia no jogo,
-- e até aqui só cabia como texto livre na descrição (V13).
--
-- Modelo: um time OU tem composição configurada (exatamente app.team.max-members
-- vagas) OU não tem nenhuma — e aí segue aceitando qualquer vocação, como sempre.
-- Nenhum time existente ganha vaga: a feature é opt-in do dono.

CREATE TABLE team_slots (
    id       BIGSERIAL PRIMARY KEY,
    list_id  BIGINT  NOT NULL REFERENCES hunting_lists (id) ON DELETE CASCADE,
    -- 1..N na ordem em que o dono configurou (a UI mostra as vagas nessa ordem).
    position INT     NOT NULL,
    -- NULO = vaga livre (aceita qualquer vocação). É o default de quem não quer
    -- restringir aquela posição.
    vocation VARCHAR(20),
    CONSTRAINT ux_team_slots_list_position UNIQUE (list_id, position),
    CONSTRAINT chk_team_slots_position CHECK (position >= 1),
    CONSTRAINT chk_team_slots_vocation
        CHECK (vocation IS NULL OR vocation IN
               ('KNIGHT', 'PALADIN', 'SORCERER', 'DRUID', 'MONK', 'NONE'))
);

CREATE INDEX ix_team_slots_list ON team_slots (list_id);

-- Qual vaga cada membro ocupa. Nulo em duas situações legítimas: time sem
-- composição configurada, e pedido ainda PENDENTE (a vaga é atribuída na
-- aprovação — pedido não reserva vaga, senão cinco pedidos travariam o time).
ALTER TABLE list_memberships
    ADD COLUMN slot_id BIGINT REFERENCES team_slots (id) ON DELETE SET NULL;

-- "Esta vaga está ocupada?" é a pergunta mais frequente do fluxo de entrada:
-- membership ATIVA e APROVADA apontando para a vaga. Índice parcial no mesmo
-- espírito dos outros (só o que o fluxo realmente busca).
CREATE INDEX ix_list_memberships_slot_occupied
    ON list_memberships (slot_id)
    WHERE slot_id IS NOT NULL AND active = TRUE AND status = 'APPROVED';
