package com.exivamoeres.service;

import com.exivamoeres.client.TibiaCharacterSnapshot;
import com.exivamoeres.domain.Character;

/**
 * Sincroniza os campos de Character que vêm da TibiaData (name/world/vocation)
 * sempre que o personagem é consultado — usado pelo fluxo de claim e pela
 * checagem de elegibilidade de time, para não duplicar a lógica de sync.
 */
public interface CharacterSyncService {

    /** Busca (ou cria) o Character local e sincroniza os campos com o snapshot. */
    Character findOrCreateFromSnapshot(TibiaCharacterSnapshot snapshot);
}
