-- O que faltava para a caçada acontecer: QUANDO o time joga e ONDE as pessoas
-- se falam. Antes, essas duas informações só existiam no chat do time — que só
-- é acessível DEPOIS de entrar. Quem estava procurando não tinha como decidir se
-- o time servia para o horário dele.
-- Aditiva e toda opcional: os times que já existem continuam válidos com nulo.

-- Texto livre do dono: estratégia, requisitos, regras da divisão de loot.
ALTER TABLE hunting_lists ADD COLUMN description VARCHAR(500);

-- Horário da caçada como TEXTO LIVRE de propósito ("Seg–Sex 20h BRT").
-- Modelar fuso/dia-da-semana de verdade é um poço sem fundo, e ainda não se sabe
-- como as pessoas vão preencher. Só vale estruturar se virar filtro da busca.
ALTER TABLE hunting_lists ADD COLUMN hunt_schedule VARCHAR(120);

-- Contato (Discord, tag no jogo). ⚠️ É DADO PESSOAL: sai apenas no detalhe do
-- time e apenas para o dono e membros APROVADOS — nunca na busca pública, que é
-- por isso que a coluna não aparece em ListSummaryResponse.
ALTER TABLE hunting_lists ADD COLUMN contact VARCHAR(120);
