package com.exivamoeres.service.impl;

import com.exivamoeres.domain.Character;
import com.exivamoeres.domain.CharacterSoulcore;
import com.exivamoeres.domain.Creature;
import com.exivamoeres.domain.HuntingList;
import com.exivamoeres.domain.ListSoulcore;
import com.exivamoeres.domain.SoulcoreStatus;
import com.exivamoeres.domain.exception.BusinessRuleException;
import com.exivamoeres.domain.exception.ResourceNotFoundException;
import com.exivamoeres.dto.soulcore.CharacterSoulcoreResponse;
import com.exivamoeres.dto.soulcore.ListSoulcoreResponse;
import com.exivamoeres.repository.CharacterSoulcoreRepository;
import com.exivamoeres.repository.CreatureRepository;
import com.exivamoeres.repository.HuntingListRepository;
import com.exivamoeres.repository.ListSoulcoreRepository;
import com.exivamoeres.service.SoulcoreService;
import com.exivamoeres.service.SuggestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class SoulcoreServiceImpl implements SoulcoreService {

    private final ListSoulcoreRepository listSoulcoreRepository;
    private final CharacterSoulcoreRepository characterSoulcoreRepository;
    private final HuntingListRepository listRepository;
    private final CreatureRepository creatureRepository;
    private final TeamMembershipGuard membershipGuard;
    private final SuggestionService suggestionService;

    public SoulcoreServiceImpl(ListSoulcoreRepository listSoulcoreRepository,
                               CharacterSoulcoreRepository characterSoulcoreRepository,
                               HuntingListRepository listRepository,
                               CreatureRepository creatureRepository,
                               TeamMembershipGuard membershipGuard,
                               SuggestionService suggestionService) {
        this.listSoulcoreRepository = listSoulcoreRepository;
        this.characterSoulcoreRepository = characterSoulcoreRepository;
        this.listRepository = listRepository;
        this.creatureRepository = creatureRepository;
        this.membershipGuard = membershipGuard;
        this.suggestionService = suggestionService;
    }

    @Override
    @Transactional
    public ListSoulcoreResponse markObtained(Long userId, Long listId, Long creatureId, Long characterId) {
        Character character = membershipGuard.requireActiveMember(userId, listId, characterId);
        HuntingList list = loadList(listId);
        Creature creature = loadCreature(creatureId);

        ListSoulcore soulcore = listSoulcoreRepository
                .findByListIdAndCreatureId(listId, creatureId)
                .orElseGet(() -> newListSoulcore(list, creature));
        if (soulcore.getStatus() == SoulcoreStatus.UNLOCKED) {
            throw new BusinessRuleException("Este core já foi desbloqueado no Soulpit");
        }
        soulcore.setStatus(SoulcoreStatus.OBTAINED);
        soulcore.setObtainedBy(character);
        listSoulcoreRepository.save(soulcore);

        log.info("soulcore.obtained listId={} creatureId={} characterId={}", listId, creatureId, characterId);
        return ListSoulcoreResponse.from(soulcore);
    }

    @Override
    @Transactional
    public ListSoulcoreResponse markUnlocked(Long userId, Long listId, Long creatureId, Long characterId) {
        Character character = membershipGuard.requireActiveMember(userId, listId, characterId);
        HuntingList list = loadList(listId);
        Creature creature = loadCreature(creatureId);

        ListSoulcore soulcore = listSoulcoreRepository
                .findByListIdAndCreatureId(listId, creatureId)
                .orElseGet(() -> newListSoulcore(list, creature));
        soulcore.setStatus(SoulcoreStatus.UNLOCKED);
        soulcore.setObtainedBy(character);
        listSoulcoreRepository.save(soulcore);

        // Registra o Animus Mastery do personagem na MESMA transação (idempotente).
        if (!characterSoulcoreRepository.existsByCharacterIdAndCreatureId(characterId, creatureId)) {
            CharacterSoulcore unlocked = new CharacterSoulcore();
            unlocked.setCharacter(character);
            unlocked.setCreature(creature);
            characterSoulcoreRepository.save(unlocked);
        }

        // Um membro desbloqueou: sugere pros demais que ainda não têm.
        suggestionService.generateSuggestions(listId);

        log.info("soulcore.unlocked listId={} creatureId={} characterId={}", listId, creatureId, characterId);
        return ListSoulcoreResponse.from(soulcore);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CharacterSoulcoreResponse> listCharacterSoulcores(Long characterId) {
        return characterSoulcoreRepository.findAllByCharacterId(characterId).stream()
                .map(CharacterSoulcoreResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ListSoulcoreResponse> listBoard(Long listId) {
        return listSoulcoreRepository.findAllByListId(listId).stream()
                .map(ListSoulcoreResponse::from)
                .toList();
    }

    private ListSoulcore newListSoulcore(HuntingList list, Creature creature) {
        ListSoulcore soulcore = new ListSoulcore();
        soulcore.setList(list);
        soulcore.setCreature(creature);
        return soulcore;
    }

    private HuntingList loadList(Long listId) {
        return listRepository.findById(listId)
                .orElseThrow(() -> new ResourceNotFoundException("Time não encontrado"));
    }

    private Creature loadCreature(Long creatureId) {
        return creatureRepository.findById(creatureId)
                .orElseThrow(() -> new ResourceNotFoundException("Criatura não encontrada"));
    }
}
