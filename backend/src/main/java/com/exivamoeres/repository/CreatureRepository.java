package com.exivamoeres.repository;

import com.exivamoeres.domain.Creature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CreatureRepository extends JpaRepository<Creature, Long> {

    Optional<Creature> findByNameIgnoreCase(String name);

    List<Creature> findAllByImageUrlIsNullAndRaceIsNotNull();

    /** Ordenação para as sugestões (prioriza menor dificuldade). */
    List<Creature> findAllByOrderByDifficultyAscNameAsc();

    /** Ordenação alfabética para os seletores da UI. */
    List<Creature> findAllByOrderByNameAsc();
}
