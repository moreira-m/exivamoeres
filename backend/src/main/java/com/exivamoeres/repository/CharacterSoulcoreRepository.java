package com.exivamoeres.repository;

import com.exivamoeres.domain.CharacterSoulcore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CharacterSoulcoreRepository extends JpaRepository<CharacterSoulcore, Long> {

    List<CharacterSoulcore> findAllByCharacterId(Long characterId);

    List<CharacterSoulcore> findAllByCharacterIdIn(Collection<Long> characterIds);

    boolean existsByCharacterIdAndCreatureId(Long characterId, Long creatureId);
}
