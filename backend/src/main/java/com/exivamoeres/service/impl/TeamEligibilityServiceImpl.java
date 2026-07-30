package com.exivamoeres.service.impl;

import com.exivamoeres.client.TibiaCharacterSnapshot;
import com.exivamoeres.config.TeamProperties;
import com.exivamoeres.domain.Character;
import com.exivamoeres.domain.exception.BusinessRuleException;
import com.exivamoeres.dto.error.ErrorCode;
import com.exivamoeres.service.CharacterSyncService;
import com.exivamoeres.service.TeamEligibilityService;
import org.springframework.stereotype.Service;

@Service
public class TeamEligibilityServiceImpl implements TeamEligibilityService {

    private final CachedCharacterLookup cachedCharacterLookup;
    private final CharacterSyncService characterSyncService;
    private final TeamProperties teamProperties;

    public TeamEligibilityServiceImpl(CachedCharacterLookup cachedCharacterLookup,
                                      CharacterSyncService characterSyncService,
                                      TeamProperties teamProperties) {
        this.cachedCharacterLookup = cachedCharacterLookup;
        this.characterSyncService = characterSyncService;
        this.teamProperties = teamProperties;
    }

    @Override
    public TibiaCharacterSnapshot assertEligible(Character character, String teamWorld, Integer minimumLevel) {
        TibiaCharacterSnapshot snapshot = cachedCharacterLookup.fetch(character.getName());
        if (!snapshot.found()) {
            throw new BusinessRuleException(ErrorCode.CHARACTER_NOT_FOUND,
                    "Personagem '" + character.getName() + "' não encontrado no Tibia.com")
                    .with("character", character.getName());
        }
        // Sincroniza world/vocação (pode ter mudado desde o claim) antes de validar.
        characterSyncService.findOrCreateFromSnapshot(snapshot);

        if (!snapshot.world().equalsIgnoreCase(teamWorld)) {
            throw new BusinessRuleException(ErrorCode.WORLD_MISMATCH,
                    "Personagem '" + character.getName() + "' é do world " + snapshot.world()
                            + ", mas o time é do world " + teamWorld)
                    .with("character", character.getName())
                    .with("characterWorld", snapshot.world())
                    .with("teamWorld", teamWorld);
        }
        // Allowlist administrativo (env TEAM_PREMIUM_BYPASS_CHARACTERS): personagens
        // listados furam a exigência de Premium Account, para testes/uso interno.
        if (!snapshot.isPremium() && !teamProperties.isPremiumBypassed(character.getName())) {
            throw new BusinessRuleException(ErrorCode.FREE_ACCOUNT,
                    "Personagem '" + character.getName() + "' é Free Account e não pode participar de times")
                    .with("character", character.getName());
        }
        if (minimumLevel != null) {
            Integer level = snapshot.level();
            if (level == null || level < minimumLevel) {
                throw new BusinessRuleException(ErrorCode.BELOW_MINIMUM_LEVEL,
                        "Este time exige level mínimo " + minimumLevel
                                + ", mas '" + character.getName() + "' tem level "
                                + (level == null ? "desconhecido" : level))
                        .with("character", character.getName())
                        .with("minimum", minimumLevel)
                        // `level` ausente é informação: "desconhecido" é o que a tela mostra.
                        .with("level", level == null ? "?" : level);
            }
        }
        return snapshot;
    }
}
