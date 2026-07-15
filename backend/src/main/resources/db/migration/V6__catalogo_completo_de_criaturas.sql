-- O catálogo completo de criaturas é importado da TibiaData no boot
-- (CreatureCatalogService), pois uma migration não pode chamar API externa.
--
-- A TibiaData NÃO expõe a dificuldade (estrelas do Bestiary), então difficulty
-- passa a ser opcional: fica nula nas criaturas importadas e mantém o valor
-- nos 12 seeds da V3 (usados nos testes e nas sugestões priorizadas).
ALTER TABLE creatures ALTER COLUMN difficulty DROP NOT NULL;

-- A CHECK (difficulty BETWEEN 1 AND 5) já aceita NULL (em SQL, NULL não é
-- FALSE), então não precisa mudar. race/image_url já existem desde a V5.
