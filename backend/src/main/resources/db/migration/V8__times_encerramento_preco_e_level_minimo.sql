-- Encerramento manual do time (item 6), preço informativo por vaga (item 4) e
-- level mínimo de entrada (item 2). Aditiva: não edita migrations aplicadas.

-- CLOSED: encerrado pelo dono (distinto de ARCHIVED = expirado pelo sistema).
-- Ambos somem da busca e viram somente leitura; CLOSED não é renovável.
ALTER TABLE hunting_lists
    DROP CONSTRAINT chk_hunting_lists_status;
ALTER TABLE hunting_lists
    ADD CONSTRAINT chk_hunting_lists_status
        CHECK (status IN ('ACTIVE', 'COMPLETED', 'ARCHIVED', 'CLOSED'));

-- Level mínimo exigido para entrar (opcional; nulo = sem restrição).
ALTER TABLE hunting_lists ADD COLUMN minimum_level INT;
ALTER TABLE hunting_lists
    ADD CONSTRAINT chk_hunting_lists_minimum_level
        CHECK (minimum_level IS NULL OR minimum_level > 0);

-- Preço por vaga: valor INFORMATIVO em gold do jogo definido pelo criador.
-- NÃO é uma transação/pagamento do sistema (não confundir com o Stripe, que é
-- assinatura do site). Opcional; nulo = não informado.
ALTER TABLE hunting_lists ADD COLUMN price_per_slot BIGINT;
ALTER TABLE hunting_lists
    ADD CONSTRAINT chk_hunting_lists_price_per_slot
        CHECK (price_per_slot IS NULL OR price_per_slot >= 0);
