-- Sessão 2: redefine hunting_lists como "time" focado numa criatura-alvo,
-- adiciona fluxo de aprovação de entrada e vincula chat a personagem.
-- Não altera V1-V4 (já aplicadas) — só ALTER/CREATE novos.

ALTER TABLE hunting_lists
    ADD COLUMN target_creature_id BIGINT REFERENCES creatures (id),
    ADD COLUMN join_policy VARCHAR(20) NOT NULL DEFAULT 'MANUAL_APPROVAL',
    ADD CONSTRAINT chk_hunting_lists_join_policy
        CHECK (join_policy IN ('MANUAL_APPROVAL', 'AUTO_ACCEPT'));

-- Backfill não é necessário (tabela vazia em qualquer ambiente pré-lançamento).
ALTER TABLE hunting_lists ALTER COLUMN target_creature_id SET NOT NULL;

CREATE INDEX ix_hunting_lists_target_creature ON hunting_lists (target_creature_id);

ALTER TABLE list_memberships
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'APPROVED',
    ADD CONSTRAINT chk_list_memberships_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'));

-- O criador varre pedidos pendentes de um time; índice parcial no mesmo
-- espírito do ix_character_claims_pending (só o que o job/UI realmente busca).
CREATE INDEX ix_list_memberships_pending
    ON list_memberships (list_id)
    WHERE status = 'PENDING' AND active = TRUE;

-- Chat vinculado a personagem (regra herdada da sessão 1): um usuário pode
-- falar "como" um personagem diferente em cada time.
ALTER TABLE chat_messages
    ADD COLUMN character_id BIGINT REFERENCES characters (id);
ALTER TABLE chat_messages ALTER COLUMN character_id SET NOT NULL;

CREATE INDEX ix_chat_messages_character ON chat_messages (character_id);

-- Vocação sincronizada da TibiaData junto com name/world (claim e checagens
-- de elegibilidade) — usada só para exibição na UI de membros do time.
ALTER TABLE characters ADD COLUMN vocation VARCHAR(30);

-- Slug usado pela TibiaData para resolver o ícone da criatura, e a URL da
-- imagem já resolvida (cache local — evita bater na TibiaData a cada request).
ALTER TABLE creatures ADD COLUMN race VARCHAR(80);
ALTER TABLE creatures ADD COLUMN image_url VARCHAR(300);

UPDATE creatures SET race = 'rat' WHERE name = 'Rat';
UPDATE creatures SET race = 'caverat' WHERE name = 'Cave Rat';
UPDATE creatures SET race = 'rotworm' WHERE name = 'Rotworm';
UPDATE creatures SET race = 'troll' WHERE name = 'Troll';
UPDATE creatures SET race = 'cyclops' WHERE name = 'Cyclops';
UPDATE creatures SET race = 'dragon' WHERE name = 'Dragon';
UPDATE creatures SET race = 'dragonlord' WHERE name = 'Dragon Lord';
UPDATE creatures SET race = 'giantspider' WHERE name = 'Giant Spider';
UPDATE creatures SET race = 'demon' WHERE name = 'Demon';
UPDATE creatures SET race = 'grimreaper' WHERE name = 'Grim Reaper';
UPDATE creatures SET race = 'juggernaut' WHERE name = 'Juggernaut';
-- Ferumbras Mortal Shell é um boss (não aparece no endpoint /v4/creature da
-- TibiaData); o ícone cai no fallback de iniciais na UI. Slug no formato
-- correto caso um /v4/boss seja usado no futuro.
UPDATE creatures SET race = 'ferumbrasmortalshell' WHERE name = 'Ferumbras Mortal Shell';
