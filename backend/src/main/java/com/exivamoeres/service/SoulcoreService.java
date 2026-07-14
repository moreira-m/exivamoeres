package com.exivamoeres.service;

/**
 * ESQUELETO PARA A SESSÃO 2 — rastreamento de soul cores nas listas.
 * Entidades e migrations (list_soulcores, character_soulcores) já existem.
 */
public interface SoulcoreService {

    /**
     * Marca que um personagem lootou o core de uma criatura na lista
     * (status OBTAINED). Só membros ativos da lista podem marcar.
     * TODO(sessão 2): implementar.
     */
    Object markObtained(Long userId, Long listId, Long creatureId, Long characterId);

    /**
     * Marca o core como gasto no Soulpit (OBTAINED -> UNLOCKED) e registra o
     * Animus Mastery em character_soulcores na MESMA transação.
     * TODO(sessão 2): implementar.
     */
    Object markUnlocked(Long userId, Long listId, Long creatureId, Long characterId);

    /** TODO(sessão 2): cores desbloqueados de um personagem (perfil). */
    Object listCharacterSoulcores(Long userId, Long characterId);

    /** TODO(sessão 2): estado de todos os cores de uma lista. */
    Object listBoard(Long userId, Long listId);
}
