package com.exivamoeres.integration;

import com.exivamoeres.domain.Character;
import com.exivamoeres.domain.JoinPolicy;
import com.exivamoeres.domain.SoulcoreStatus;
import com.exivamoeres.domain.User;
import com.exivamoeres.dto.list.CreateListRequest;
import com.exivamoeres.dto.list.ListDetailResponse;
import com.exivamoeres.dto.soulcore.ListSoulcoreResponse;
import com.exivamoeres.repository.CharacterSoulcoreRepository;
import com.exivamoeres.service.HuntingListService;
import com.exivamoeres.service.SoulcoreService;
import com.exivamoeres.service.SuggestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/** Fluxo de soul cores: obtain, unlock (grava Animus Mastery) e geração de sugestões. */
class SoulcoreSuggestionIntegrationTest extends TeamIntegrationTestBase {

    @Autowired HuntingListService listService;
    @Autowired SoulcoreService soulcoreService;
    @Autowired SuggestionService suggestionService;
    @Autowired CharacterSoulcoreRepository characterSoulcoreRepository;

    @Test
    void unlockRegistraAnimusMasteryEGeraSugestoes() {
        User owner = createUser("owner-sc@teste.com");
        Character ownerChar = createCharacter("Owner SC", "Antica", owner);
        stubPremium("Owner SC", "Antica");
        ListDetailResponse team = listService.createList(owner.getId(), new CreateListRequest(
                "Caça ao Demon", "Antica", creature("Demon").getId(),
                JoinPolicy.AUTO_ACCEPT, ownerChar.getId(), null, null));
        Long listId = team.summary().id();
        Long dragonId = creature("Dragon").getId();

        // Marca obtido e depois desbloqueado.
        soulcoreService.markObtained(owner.getId(), listId, dragonId, ownerChar.getId());
        ListSoulcoreResponse unlocked = soulcoreService.markUnlocked(
                owner.getId(), listId, dragonId, ownerChar.getId());

        assertThat(unlocked.status()).isEqualTo(SoulcoreStatus.UNLOCKED);
        // Animus Mastery gravado para o personagem.
        assertThat(characterSoulcoreRepository.existsByCharacterIdAndCreatureId(ownerChar.getId(), dragonId))
                .isTrue();

        // Sugestões geradas para o time: criaturas que ninguém tem, sem o Dragon
        // (já desbloqueado) nem o Demon (alvo do próprio time).
        var suggestions = suggestionService.listSuggestions(owner.getId(), listId);
        assertThat(suggestions).isNotEmpty();
        assertThat(suggestions).noneMatch(s -> s.creatureName().equals("Dragon"));
        assertThat(suggestions).noneMatch(s -> s.creatureName().equals("Demon"));
        // Prioridade por menor difficulty: a primeira sugestão tem difficulty mínima.
        int firstDifficulty = suggestions.get(0).difficulty();
        assertThat(suggestions).allMatch(s -> s.difficulty() >= firstDifficulty);
    }

    @Test
    void naoMembroNaoPodeMarcarCore() {
        User owner = createUser("owner-nm@teste.com");
        Character ownerChar = createCharacter("Owner NM", "Antica", owner);
        stubPremium("Owner NM", "Antica");
        ListDetailResponse team = listService.createList(owner.getId(), new CreateListRequest(
                "Time NM", "Antica", creature("Demon").getId(),
                JoinPolicy.AUTO_ACCEPT, ownerChar.getId(), null, null));

        User stranger = createUser("stranger@teste.com");
        Character strangerChar = createCharacter("Stranger", "Antica", stranger);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                soulcoreService.markObtained(stranger.getId(), team.summary().id(),
                        creature("Dragon").getId(), strangerChar.getId()))
                .hasMessageContaining("não é membro");
    }
}
