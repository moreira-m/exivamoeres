package com.exivamoeres.service.impl;

import com.exivamoeres.client.TibiaCharacterSnapshot;
import com.exivamoeres.domain.Character;
import com.exivamoeres.repository.CharacterRepository;
import com.exivamoeres.service.CharacterSyncService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CharacterSyncServiceImpl implements CharacterSyncService {

    private final CharacterRepository characterRepository;

    public CharacterSyncServiceImpl(CharacterRepository characterRepository) {
        this.characterRepository = characterRepository;
    }

    @Override
    @Transactional
    public Character findOrCreateFromSnapshot(TibiaCharacterSnapshot snapshot) {
        Character character = characterRepository.findByNameIgnoreCase(snapshot.name())
                .orElseGet(() -> {
                    Character created = new Character();
                    created.setName(snapshot.name());
                    return created;
                });
        // Nome, mundo e vocação sempre sincronizados com o Tibia.com (world
        // transfer, mudança de vocação e formatação canônica do nome).
        character.setName(snapshot.name());
        character.setWorld(snapshot.world());
        character.setVocation(snapshot.vocation());
        return characterRepository.save(character);
    }
}
