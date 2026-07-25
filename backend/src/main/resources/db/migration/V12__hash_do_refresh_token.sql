-- Refresh token deixa de ser guardado em texto puro: a coluna passa a conter o
-- SHA-256 do valor, que só existe cru na resposta HTTP. Assim, uma leitura
-- indevida do banco (backup vazado, acesso ao painel, log de query) não entrega
-- mais sessões válidas de todos os usuários.

-- Hash é de mão única: não há como converter os tokens existentes. Apagar é a
-- migração possível — cada usuário faz login de novo uma vez.
DELETE FROM refresh_tokens;

ALTER TABLE refresh_tokens RENAME COLUMN token TO token_hash;

-- SHA-256 em hexadecimal tem exatamente 64 caracteres.
ALTER TABLE refresh_tokens ALTER COLUMN token_hash TYPE VARCHAR(64);

-- O índice único veio da constraint inline da V1 (nome gerado pelo Postgres):
-- renomear deixa a intenção explícita para quem for ler o schema depois.
ALTER INDEX refresh_tokens_token_key RENAME TO ux_refresh_tokens_token_hash;
