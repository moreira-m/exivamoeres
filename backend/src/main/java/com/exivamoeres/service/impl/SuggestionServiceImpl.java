package com.exivamoeres.service.impl;

import com.exivamoeres.domain.Creature;
import com.exivamoeres.domain.HuntingList;
import com.exivamoeres.domain.ListMembership;
import com.exivamoeres.domain.MembershipStatus;
import com.exivamoeres.domain.SoulcoreSuggestion;
import com.exivamoeres.domain.exception.BusinessRuleException;
import com.exivamoeres.domain.exception.ResourceNotFoundException;
import com.exivamoeres.dto.suggestion.SuggestionResponse;
import com.exivamoeres.repository.CharacterSoulcoreRepository;
import com.exivamoeres.repository.CreatureRepository;
import com.exivamoeres.repository.HuntingListRepository;
import com.exivamoeres.repository.ListMembershipRepository;
import com.exivamoeres.repository.SoulcoreSuggestionRepository;
import com.exivamoeres.service.SuggestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SuggestionServiceImpl implements SuggestionService {

    /** Teto de sugestões geradas por vez — evita floodar a UI. */
    private static final int MAX_SUGGESTIONS = 5;

    private final SoulcoreSuggestionRepository suggestionRepository;
    private final ListMembershipRepository membershipRepository;
    private final CharacterSoulcoreRepository characterSoulcoreRepository;
    private final CreatureRepository creatureRepository;
    private final HuntingListRepository listRepository;
    private final TeamMembershipGuard membershipGuard;

    public SuggestionServiceImpl(SoulcoreSuggestionRepository suggestionRepository,
                                 ListMembershipRepository membershipRepository,
                                 CharacterSoulcoreRepository characterSoulcoreRepository,
                                 CreatureRepository creatureRepository,
                                 HuntingListRepository listRepository,
                                 TeamMembershipGuard membershipGuard) {
        this.suggestionRepository = suggestionRepository;
        this.membershipRepository = membershipRepository;
        this.characterSoulcoreRepository = characterSoulcoreRepository;
        this.creatureRepository = creatureRepository;
        this.listRepository = listRepository;
        this.membershipGuard = membershipGuard;
    }

    @Override
    @Transactional
    public void generateSuggestions(Long listId) {
        HuntingList list = listRepository.findById(listId)
                .orElseThrow(() -> new ResourceNotFoundException("Time não encontrado"));

        List<Long> memberCharacterIds = membershipRepository
                .findAllByListIdAndStatusAndActiveTrue(listId, MembershipStatus.APPROVED).stream()
                .map(m -> m.getCharacter().getId())
                .toList();
        if (memberCharacterIds.isEmpty()) {
            return;
        }

        // Cores que ALGUÉM do time já desbloqueou não interessam como sugestão.
        Set<Long> unlockedCreatureIds = characterSoulcoreRepository
                .findAllByCharacterIdIn(memberCharacterIds).stream()
                .map(cs -> cs.getCreature().getId())
                .collect(Collectors.toSet());

        // Criaturas que ninguém tem, priorizadas por menor difficulty.
        List<Creature> candidates = creatureRepository.findAllByOrderByDifficultyAscNameAsc().stream()
                .filter(creature -> !unlockedCreatureIds.contains(creature.getId()))
                // O alvo do próprio time não é "próximo boss" a sugerir.
                .filter(creature -> !creature.getId().equals(list.getTargetCreature().getId()))
                .filter(creature -> !suggestionRepository
                        .existsByListIdAndCreatureIdAndDismissedFalse(listId, creature.getId()))
                .limit(MAX_SUGGESTIONS)
                .toList();

        for (Creature creature : candidates) {
            SoulcoreSuggestion suggestion = new SoulcoreSuggestion();
            suggestion.setList(list);
            suggestion.setCreature(creature);
            suggestion.setReason(buildReason(creature));
            suggestionRepository.save(suggestion);
        }
        if (!candidates.isEmpty()) {
            log.info("suggestions.generated listId={} count={}", listId, candidates.size());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<SuggestionResponse> listSuggestions(Long userId, Long listId) {
        membershipGuard.requireActiveMember(userId, listId);
        return suggestionRepository.findAllByListIdAndDismissedFalse(listId).stream()
                .map(SuggestionResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public void dismissSuggestion(Long userId, Long suggestionId) {
        SoulcoreSuggestion suggestion = suggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new ResourceNotFoundException("Sugestão não encontrada"));
        membershipGuard.requireActiveMember(userId, suggestion.getList().getId());
        if (suggestion.isDismissed()) {
            throw new BusinessRuleException("Sugestão já foi descartada");
        }
        suggestion.setDismissed(true);
    }

    private String buildReason(Creature creature) {
        String base = "Nenhum membro do time tem o core de " + creature.getName();
        return creature.getDifficulty() != null
                ? base + " (dificuldade " + creature.getDifficulty() + ")"
                : base;
    }
}
