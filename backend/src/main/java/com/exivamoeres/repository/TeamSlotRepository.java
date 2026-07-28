package com.exivamoeres.repository;

import com.exivamoeres.domain.TeamSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeamSlotRepository extends JpaRepository<TeamSlot, Long> {

    /** Vagas do time na ordem em que o dono configurou. */
    List<TeamSlot> findAllByListIdOrderByPosition(Long listId);

    /** Time sem vaga = sem composição configurada (comportamento pré-V17). */
    boolean existsByListId(Long listId);
}
