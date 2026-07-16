package com.exivamoeres.service;

import com.exivamoeres.client.TibiaCharacterSnapshot;
import com.exivamoeres.domain.Character;

/**
 * Valida as regras de elegibilidade de personagem para participar de um time
 * (SEMPRE no backend — o frontend só melhora a UX):
 * - o personagem precisa ser do mesmo world do time;
 * - contas Free Account nunca podem participar;
 * - o personagem precisa ter o level mínimo exigido pelo time (se houver).
 *
 * A consulta à TibiaData usada aqui é cacheada (TTL configurável) para não
 * bater na API a cada ação do usuário.
 */
public interface TeamEligibilityService {

    /**
     * Busca um snapshot fresco (cacheado) do personagem na TibiaData,
     * sincroniza os campos locais e valida a elegibilidade. Lança
     * BusinessRuleException com mensagem clara se inelegível.
     *
     * @param minimumLevel level mínimo exigido, ou null se sem restrição
     * @return o snapshot da TibiaData (reaproveitável para checar vocação/vaga)
     */
    TibiaCharacterSnapshot assertEligible(Character character, String teamWorld, Integer minimumLevel);
}
