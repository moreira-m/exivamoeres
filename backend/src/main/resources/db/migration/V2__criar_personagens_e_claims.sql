-- Personagens do Tibia. user_id nulo = personagem ainda sem dono verificado.
CREATE TABLE characters (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(60) NOT NULL,
    world      VARCHAR(40) NOT NULL,
    user_id    BIGINT REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Nomes no Tibia são únicos globalmente e case-insensitive.
CREATE UNIQUE INDEX ux_characters_name_lower ON characters (lower(name));
CREATE INDEX ix_characters_user ON characters (user_id);

-- Claims de posse de personagem, verificados via comment no Tibia.com.
CREATE TABLE character_claims (
    id                BIGSERIAL PRIMARY KEY,
    character_id      BIGINT      NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    user_id           BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    verification_code VARCHAR(20) NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    last_checked_at   TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at       TIMESTAMPTZ,
    CONSTRAINT chk_claims_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

-- Índice parcial: o job de polling só varre claims pendentes; a tabela tende
-- a acumular histórico de aprovados/rejeitados que não interessa ao scan.
CREATE INDEX ix_character_claims_pending
    ON character_claims (status)
    WHERE status = 'PENDING';

CREATE INDEX ix_character_claims_user ON character_claims (user_id);
CREATE INDEX ix_character_claims_character ON character_claims (character_id);
