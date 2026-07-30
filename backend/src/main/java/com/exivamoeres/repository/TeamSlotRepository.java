package com.exivamoeres.repository;

import com.exivamoeres.domain.TeamSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TeamSlotRepository extends JpaRepository<TeamSlot, Long> {

    /** Vagas do time na ordem em que o dono configurou. */
    List<TeamSlot> findAllByListIdOrderByPosition(Long listId);

    /**
     * Vagas de vários times de uma vez — para a tela "meus pedidos", que precisa da
     * composição de cada time da lista. Uma consulta em vez de uma por pedido.
     */
    List<TeamSlot> findAllByListIdIn(Collection<Long> listIds);

    /** Time sem vaga = sem composição configurada (comportamento pré-V17). */
    boolean existsByListId(Long listId);
}
