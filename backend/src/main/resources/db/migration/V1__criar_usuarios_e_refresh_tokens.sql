-- Usuários: locais (email+senha), OAuth (Google/Discord) e anônimos.
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) UNIQUE,
    password_hash VARCHAR(100),
    display_name  VARCHAR(100) NOT NULL,
    auth_provider VARCHAR(20)  NOT NULL,
    provider_id   VARCHAR(100),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_users_auth_provider
        CHECK (auth_provider IN ('LOCAL', 'GOOGLE', 'DISCORD', 'ANONYMOUS'))
);

-- Localização rápida de conta OAuth existente no callback de login social.
CREATE UNIQUE INDEX ux_users_provider
    ON users (auth_provider, provider_id)
    WHERE provider_id IS NOT NULL;

-- Refresh tokens opacos: revogáveis individualmente (logout / vazamento).
CREATE TABLE refresh_tokens (
    id         BIGSERIAL PRIMARY KEY,
    token      VARCHAR(100) NOT NULL UNIQUE,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ  NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);
