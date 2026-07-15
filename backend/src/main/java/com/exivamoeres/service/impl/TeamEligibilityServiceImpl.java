package com.exivamoeres.service.impl;

import com.exivamoeres.client.TibiaCharacterSnapshot;
import com.exivamoeres.domain.Character;
import com.exivamoeres.domain.exception.BusinessRuleException;
import com.exivamoeres.service.CharacterSyncService;
import com.exivamoeres.service.TeamEligibilityService;
import org.springframework.stereotype.Service;

@Service
public class TeamEligibilityServiceImpl implements TeamEligibilityService {

    private final CachedCharacterLookup cachedCharacterLookup;
    private final CharacterSyncService characterSyncService;

    public TeamEligibilityServiceImpl(CachedCharacterLookup cachedCharacterLookup,
                                      CharacterSyncService characterSyncService) {
        this.cachedCharacterLookup = cachedCharacterLookup;
        this.characterSyncService = characterSyncService;
    }

    @Override
    public void assertEligible(Character character, String teamWorld) {
        TibiaCharacterSnapshot snapshot = cachedCharacterLookup.fetch(character.getName());
        if (!snapshot.found()) {
            throw new BusinessRuleException(
                    "Personagem '" + character.getName() + "' não encontrado no Tibia.com");
        }
        // Sincroniza world/vocação (pode ter mudado desde o claim) antes de validar.
        characterSyncService.findOrCreateFromSnapshot(snapshot);

        if (!snapshot.world().equalsIgnoreCase(teamWorld)) {
            throw new BusinessRuleException(
                    "Personagem '" + character.getName() + "' é do world " + snapshot.world()
                            + ", mas o time é do world " + teamWorld);
        }
        if (!snapshot.isPremium()) {
            throw new BusinessRuleException(
                    "Personagem '" + character.getName() + "' é Free Account e não pode participar de times");
        }
    }
}
