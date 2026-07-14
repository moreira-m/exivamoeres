package com.exivamoeres.repository;

import com.exivamoeres.domain.Character;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CharacterRepository extends JpaRepository<Character, Long> {

    Optional<Character> findByNameIgnoreCase(String name);

    List<Character> findAllByOwnerId(Long ownerId);
}
