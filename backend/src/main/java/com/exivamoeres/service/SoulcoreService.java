package com.exivamoeres.service;

import com.exivamoeres.dto.soulcore.CharacterSoulcoreResponse;
import com.exivamoeres.dto.soulcore.ListSoulcoreResponse;

import java.util.List;

/**
 * Rastreamento de soul cores dentro de um time: quem lootou (OBTAINED) e
 * quando o core é gasto no Soulpit (UNLOCKED). Só membros ativos e aprovados
 * do time podem registrar ações, sempre com um personagem próprio.
 */
public interface SoulcoreService {

    /** Marca que um personagem lootou o core de uma criatura no time (OBTAINED). */
    ListSoulcoreResponse markObtained(Long userId, Long listId, Long creatureId, Long characterId);

    /**
     * Gasta o core no Soulpit (OBTAINED -> UNLOCKED) e registra o Animus
     * Mastery em character_soulcores na MESMA transação. Dispara a geração de
     * sugestões para os demais membros que ainda não têm esse core.
     */
    ListSoulcoreResponse markUnlocked(Long userId, Long listId, Long creatureId, Long characterId);

    /** Cores já desbloqueados por um personagem (perfil). */
    List<CharacterSoulcoreResponse> listCharacterSoulcores(Long characterId);

    /** Estado de todos os cores rastreados num time. */
    List<ListSoulcoreResponse> listBoard(Long listId);
}
