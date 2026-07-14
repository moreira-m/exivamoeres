-- Catálogo de criaturas do Bestiary (uma criatura = um soul core).
-- difficulty = estrelas do Bestiary (1 Harmless .. 5 Challenging).
CREATE TABLE creatures (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(80) NOT NULL UNIQUE,
    difficulty INT         NOT NULL,
    CONSTRAINT chk_creatures_difficulty CHECK (difficulty BETWEEN 1 AND 5)
);

-- Seed parcial para desenvolvimento e testes.
-- ATENÇÃO (sessão 2): o Bestiary completo tem centenas de criaturas; importar
-- o catálogo inteiro (ex.: via TibiaData /v4/creatures) numa migration futura.
INSERT INTO creatures (name, difficulty) VALUES
    ('Rat', 1),
    ('Cave Rat', 1),
    ('Rotworm', 1),
    ('Troll', 1),
    ('Cyclops', 2),
    ('Dragon', 2),
    ('Dragon Lord', 3),
    ('Giant Spider', 3),
    ('Demon', 4),
    ('Grim Reaper', 4),
    ('Juggernaut', 5),
    ('Ferumbras Mortal Shell', 5);
