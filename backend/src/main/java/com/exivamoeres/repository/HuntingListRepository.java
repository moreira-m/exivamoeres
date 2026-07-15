package com.exivamoeres.repository;

import com.exivamoeres.domain.HuntingList;
import com.exivamoeres.domain.TeamStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface HuntingListRepository extends JpaRepository<HuntingList, Long> {

    Optional<HuntingList> findByShareCode(String shareCode);

    List<HuntingList> findAllByOwnerId(Long ownerId);

    /** Limite de times ativos por plano (ver PlanPolicy). */
    long countByOwnerIdAndStatus(Long ownerId, TeamStatus status);

    /** Varredura do job de expiração — atende pelo índice parcial de ativos. */
    List<HuntingList> findAllByStatusAndExpiresAtBefore(TeamStatus status, Instant moment);

    /**
     * Trava a linha do time durante o join/aprovação — evita duas
     * transações concorrentes ultrapassarem o limite de 5 membros
     * (ver TeamProperties.maxMembers).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from HuntingList l where l.id = :id")
    Optional<HuntingList> findByIdForUpdate(@Param("id") Long id);

    /**
     * Busca pública (home): só times ATIVOS. Times de donos premium aparecem
     * primeiro (anúncios em destaque). O filtro de vaga disponível
     * (hasOpenSlots) é aplicado depois, em memória pelo service — contar
     * membros ativos por time exigiria subquery só pra um filtro opcional e o
     * teto de 5 membros torna o custo desprezível.
     */
    @Query("""
            select l from HuntingList l
            join fetch l.owner o
            join fetch l.targetCreature
            where l.status = com.exivamoeres.domain.TeamStatus.ACTIVE
              and (:world is null or l.world = :world)
              and (:creatureId is null or l.targetCreature.id = :creatureId)
            order by case when o.plan = com.exivamoeres.domain.Plan.PREMIUM then 0 else 1 end,
                     l.createdAt desc
            """)
    Page<HuntingList> search(@Param("world") String world, @Param("creatureId") Long creatureId, Pageable pageable);
}
