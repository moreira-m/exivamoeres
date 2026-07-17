package com.exivamoeres.service;

/**
 * Re-sincroniza periodicamente o level dos personagens que aparecem em times
 * ativos, para manter o número exibido atualizado conforme o jogador upa no
 * Tibia. Custo mínimo: escopo restrito + validade (staleness) + lote espaçado.
 */
public interface CharacterLevelRefreshService {

    /** Rebusca na TibiaData um lote de personagens de time ativo com retrato vencido. */
    void refreshStaleTeamCharacters();
}
