package com.exivamoeres.service;

import com.exivamoeres.domain.Character;

/**
 * Valida as regras de elegibilidade de personagem para participar de um time
 * (SEMPRE no backend — o frontend só melhora a UX):
 * - o personagem precisa ser do mesmo world do time;
 * - contas Free Account nunca podem participar.
 *
 * A consulta à TibiaData usada aqui é cacheada (TTL configurável) para não
 * bater na API a cada ação do usuário.
 */
public interface TeamEligibilityService {

    /**
     * Busca um snapshot fresco (cacheado) do personagem na TibiaData,
     * sincroniza os campos locais e valida a elegibilidade contra o world do
     * time. Lança BusinessRuleException com mensagem clara se inelegível.
     */
    void assertEligible(Character character, String teamWorld);
}
