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
     * primeiro (anúncios em destaque).
     *
     * O filtro de vaga disponível (onlyOpenSlots) conta os membros aprovados
     * por subquery — precisa ser aqui, e não em memória sobre a página já
     * carregada, senão o total da página mente e a home pula times ao paginar.
     *
     * O desempate por id no order by não é enfeite: sem ordenação total, dois
     * times criados no mesmo instante podem trocar de lugar entre uma página e
     * a seguinte, fazendo o "carregar mais" repetir um e esconder o outro.
     */
    @Query(value = """
            select l from HuntingList l
            join fetch l.owner o
            join fetch l.targetCreature
            where l.status = com.exivamoeres.domain.TeamStatus.ACTIVE
              and (:world is null or l.world = :world)
              and (:creatureId is null or l.targetCreature.id = :creatureId)
              and (:onlyOpenSlots = false or (
                    select count(m) from ListMembership m
                    where m.list = l
                      and m.active = true
                      and m.status = com.exivamoeres.domain.MembershipStatus.APPROVED
                  ) < :maxMembers)
            order by case when o.plan = com.exivamoeres.domain.Plan.PREMIUM then 0 else 1 end,
                     l.createdAt desc, l.id desc
            """,
            countQuery = """
            select count(l) from HuntingList l
            where l.status = com.exivamoeres.domain.TeamStatus.ACTIVE
              and (:world is null or l.world = :world)
              and (:creatureId is null or l.targetCreature.id = :creatureId)
              and (:onlyOpenSlots = false or (
                    select count(m) from ListMembership m
                    where m.list = l
                      and m.active = true
                      and m.status = com.exivamoeres.domain.MembershipStatus.APPROVED
                  ) < :maxMembers)
            """)
    Page<HuntingList> search(@Param("world") String world,
                             @Param("creatureId") Long creatureId,
                             @Param("onlyOpenSlots") boolean onlyOpenSlots,
                             @Param("maxMembers") int maxMembers,
                             Pageable pageable);
}
