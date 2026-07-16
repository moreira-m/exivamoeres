-- Level do personagem, sincronizado da TibiaData (exibido na lista de membros).
-- Nulo até a primeira sincronização (claim ou entrada em time).
ALTER TABLE characters ADD COLUMN level INT;
