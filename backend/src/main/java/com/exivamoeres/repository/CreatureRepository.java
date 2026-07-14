package com.exivamoeres.repository;

import com.exivamoeres.domain.Creature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CreatureRepository extends JpaRepository<Creature, Long> {

    Optional<Creature> findByNameIgnoreCase(String name);
}
