-- Estruturas das funcionalidades da sessão 2 (listas, soulcores, sugestões,
-- chat). Schema criado agora para o modelo de dados nascer completo; a lógica
-- de negócio ainda não existe.

CREATE TABLE hunting_lists (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    world      VARCHAR(40)  NOT NULL,
    share_code VARCHAR(20)  NOT NULL UNIQUE,
    owner_id   BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_hunting_lists_owner ON hunting_lists (owner_id);
CREATE INDEX ix_hunting_lists_world ON hunting_lists (world);

CREATE TABLE list_memberships (
    id           BIGSERIAL PRIMARY KEY,
    list_id      BIGINT      NOT NULL REFERENCES hunting_lists (id) ON DELETE CASCADE,
    user_id      BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    character_id BIGINT      NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    active       BOOLEAN     NOT NULL DEFAULT TRUE,
    joined_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Um personagem só participa uma vez de cada lista.
    CONSTRAINT ux_membership_list_character UNIQUE (list_id, character_id)
);

CREATE INDEX ix_list_memberships_user ON list_memberships (user_id);
-- A aprovação de um claim desativa memberships ativas do dono anterior.
CREATE INDEX ix_list_memberships_character_active
    ON list_memberships (character_id)
    WHERE active = TRUE;

CREATE TABLE list_soulcores (
    id                       BIGSERIAL PRIMARY KEY,
    list_id                  BIGINT      NOT NULL REFERENCES hunting_lists (id) ON DELETE CASCADE,
    creature_id              BIGINT      NOT NULL REFERENCES creatures (id),
    status                   VARCHAR(20) NOT NULL,
    obtained_by_character_id BIGINT REFERENCES characters (id) ON DELETE SET NULL,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_list_soulcores_status CHECK (status IN ('OBTAINED', 'UNLOCKED')),
    CONSTRAINT ux_list_soulcore UNIQUE (list_id, creature_id)
);

CREATE TABLE character_soulcores (
    id           BIGSERIAL PRIMARY KEY,
    character_id BIGINT      NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    creature_id  BIGINT      NOT NULL REFERENCES creatures (id),
    unlocked_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_character_soulcore UNIQUE (character_id, creature_id)
);

CREATE TABLE soulcore_suggestions (
    id          BIGSERIAL PRIMARY KEY,
    list_id     BIGINT       NOT NULL REFERENCES hunting_lists (id) ON DELETE CASCADE,
    creature_id BIGINT       NOT NULL REFERENCES creatures (id),
    reason      VARCHAR(300) NOT NULL,
    dismissed   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_soulcore_suggestions_list ON soulcore_suggestions (list_id);

CREATE TABLE chat_messages (
    id         BIGSERIAL PRIMARY KEY,
    list_id    BIGINT        NOT NULL REFERENCES hunting_lists (id) ON DELETE CASCADE,
    sender_id  BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    content    VARCHAR(1000) NOT NULL,
    sent_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Paginação do histórico do chat por lista, das mais recentes pra trás.
CREATE INDEX ix_chat_messages_list_sent ON chat_messages (list_id, sent_at DESC);
