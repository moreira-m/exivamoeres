package com.exivamoeres.repository;

import com.exivamoeres.domain.HuntingList;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HuntingListRepository extends JpaRepository<HuntingList, Long> {

    Optional<HuntingList> findByShareCode(String shareCode);

    List<HuntingList> findAllByOwnerId(Long ownerId);

    /**
     * Trava a linha do time durante o join/aprovação — evita duas
     * transações concorrentes ultrapassarem o limite de 5 membros
     * (ver TeamProperties.maxMembers).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from HuntingList l where l.id = :id")
    Optional<HuntingList> findByIdForUpdate(@Param("id") Long id);

    /**
     * Busca pública (home). O filtro de vaga disponível (hasOpenSlots) é
     * aplicado depois, em memória pelo service — contar membros ativos por
     * time exigiria uma subquery correlacionada só pra um filtro opcional;
     * dado o tamanho máximo de 5 membros por time, o custo é desprezível.
     */
    @Query("""
            select l from HuntingList l
            where (:world is null or l.world = :world)
              and (:creatureId is null or l.targetCreature.id = :creatureId)
            order by l.createdAt desc
            """)
    Page<HuntingList> search(@Param("world") String world, @Param("creatureId") Long creatureId, Pageable pageable);
}
