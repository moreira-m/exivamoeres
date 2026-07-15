-- Planos de conta (free/premium) e ciclo de vida dos times (ativo, completo,
-- arquivado). Aditiva: não edita V1-V6.

-- ----- Planos de usuário -----
-- users.plan é o cache rápido consultado em toda autorização; a tabela
-- subscriptions abaixo é a fonte de verdade/auditoria da assinatura Stripe.
ALTER TABLE users
    ADD COLUMN plan VARCHAR(20) NOT NULL DEFAULT 'FREE',
    ADD COLUMN stripe_customer_id VARCHAR(255),
    ADD CONSTRAINT chk_users_plan CHECK (plan IN ('FREE', 'PREMIUM'));

CREATE UNIQUE INDEX ux_users_stripe_customer
    ON users (stripe_customer_id)
    WHERE stripe_customer_id IS NOT NULL;

CREATE TABLE subscriptions (
    id                     BIGSERIAL PRIMARY KEY,
    user_id                BIGINT       NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    stripe_subscription_id VARCHAR(255) NOT NULL UNIQUE,
    -- Status cru do Stripe (active, past_due, canceled, ...); não reinventamos
    -- um enum próprio para não quebrar se o Stripe adicionar um status novo.
    status                 VARCHAR(40)  NOT NULL,
    current_period_end     TIMESTAMPTZ,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ----- Ciclo de vida dos times -----
-- ACTIVE: dentro do prazo (busca pública + escrita liberada).
-- COMPLETED: o core da criatura-alvo foi desbloqueado (só leitura).
-- ARCHIVED: expirou sem completar (só leitura; some da busca pública).
ALTER TABLE hunting_lists
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN expires_at TIMESTAMPTZ,
    ADD CONSTRAINT chk_hunting_lists_status
        CHECK (status IN ('ACTIVE', 'COMPLETED', 'ARCHIVED'));

-- Backfill dos times já existentes (7 dias a partir da criação) antes do NOT NULL.
UPDATE hunting_lists SET expires_at = created_at + INTERVAL '7 days' WHERE expires_at IS NULL;
ALTER TABLE hunting_lists ALTER COLUMN expires_at SET NOT NULL;

-- O scheduler varre só times ACTIVE vencidos; a busca pública lista só ACTIVE.
CREATE INDEX ix_hunting_lists_active_expiry
    ON hunting_lists (expires_at)
    WHERE status = 'ACTIVE';
