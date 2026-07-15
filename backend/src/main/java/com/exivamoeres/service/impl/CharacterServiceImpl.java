package com.exivamoeres.service.impl;

import com.exivamoeres.dto.character.CharacterSummaryResponse;
import com.exivamoeres.repository.CharacterRepository;
import com.exivamoeres.service.CharacterService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CharacterServiceImpl implements CharacterService {

    private final CharacterRepository characterRepository;

    public CharacterServiceImpl(CharacterRepository characterRepository) {
        this.characterRepository = characterRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CharacterSummaryResponse> listMyCharacters(Long userId) {
        return characterRepository.findAllByOwnerId(userId).stream()
                .map(CharacterSummaryResponse::from)
                .toList();
    }
}
