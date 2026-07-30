-- O filtro de mundos da home é o primeiro controle que todo visitante vê, e ele dependia
-- da TibiaData estar de pé **no momento em que o cache esquenta**. Como o cache é em
-- memória, "frio" acontece a cada restart: deploy, reinício do Railway, crash. Se a
-- TibiaData estivesse fora nesse instante, /api/worlds respondia 503 e a home abria sem
-- o filtro — descoberto ao montar o CI (item S13).
--
-- Esta tabela é o **chão** dessa consulta: a última lista boa fica gravada, e uma falha da
-- API externa passa a degradar para "a lista de ontem" em vez de "nenhuma lista". A lista
-- de mundos do Tibia muda algumas vezes por ano, então "de ontem" é praticamente igual a
-- "de agora" — e é infinitamente melhor que vazio.
--
-- O nome é a chave: a lista é um conjunto de nomes, sem outro atributo. `refreshed_at` diz
-- **desde quando conhecemos aquele mundo** (a sincronização insere o que apareceu e apaga o
-- que saiu, sem tocar no que continua igual) — serve para depuração, não para regra.
CREATE TABLE worlds (
    name         VARCHAR(40) PRIMARY KEY,
    refreshed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE worlds IS
    'Última lista de mundos conhecida (espelho da TibiaData). Serve de fallback quando a API externa está fora.';
