-- S8 — as duas tabelas que crescem para sempre ganham o índice da limpeza.
--
-- O job de retenção apaga, todo dia, uma fatia PEQUENA de uma tabela GRANDE:
-- em estado estacionário são as ~24h de lixo que passaram da janela, dentro de
-- uma tabela que carrega a janela inteira. Sem índice isso é um seq scan na
-- maior tabela do banco — justamente a que o job existe para manter pequena.
--
-- Medido em 300 mil linhas (Postgres 16, dados sintéticos):
--
--   refresh_tokens (7.570 alvos)      seq scan  63,3 ms → index scan  4,2 ms
--   notifications     (739 alvos)     seq scan  53,7 ms → index scan  0,9 ms
--
-- Custo: 6,6 MB e 5,3 MB sobre uma tabela de 40 MB.
--
-- ⚠️ CREATE INDEX comum (não CONCURRENTLY) porque as duas tabelas ainda são
-- pequenas em produção e o Flyway roda a migration em transação — CONCURRENTLY
-- exigiria `-- flyway:executeInTransaction=false`. Se um dia isto rodar sobre
-- uma tabela já enorme, é essa a troca a fazer.

-- A consulta é `expires_at < ?`: a data é o único filtro, e ela ordena.
-- Deliberadamente NÃO inclui `revoked` — o job não filtra por revogação (o
-- porquê está em RetentionServiceImpl: token revogado é o sinal de reuso do S7,
-- e ele só pode sumir depois de vencido).
CREATE INDEX ix_refresh_tokens_expires_at ON refresh_tokens (expires_at);

-- PARCIAL, no padrão do ix_notifications_unread (V9): o job só olha as LIDAS, e
-- notificação lida é a minoria que envelhece. O índice não paga pelas não-lidas,
-- que são as que a tela consulta a toda hora — essas já têm o índice delas.
CREATE INDEX ix_notifications_read_created ON notifications (created_at) WHERE read = true;
