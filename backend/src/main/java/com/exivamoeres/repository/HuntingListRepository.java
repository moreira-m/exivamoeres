package com.exivamoeres.repository;

import com.exivamoeres.domain.HuntingList;
import com.exivamoeres.domain.TeamStatus;
import com.exivamoeres.domain.Vocation;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
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
     *
     * <p>O filtro de <b>vocação</b> responde "onde um personagem desta vocação
     * cabe AGORA", não "quem exige esta vocação": entra o time com vaga livre
     * compatível (inclusive vaga <b>sem</b> exigência) e o time <b>sem</b>
     * composição que ainda tenha vaga. Excluir esses dois casos deixaria o filtro
     * escondendo a maior parte do site — e o que ele precisa evitar é o pedido que
     * vai ser recusado, não a lista curta.</p>
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
              and (:vocation is null or (
                    (not exists (
                        select s from TeamSlot s where s.list = l
                      ) and (
                        select count(m) from ListMembership m
                        where m.list = l
                          and m.active = true
                          and m.status = com.exivamoeres.domain.MembershipStatus.APPROVED
                      ) < :maxMembers)
                    or exists (
                        select s from TeamSlot s
                        where s.list = l
                          and (s.vocation is null or s.vocation = :vocation)
                          and not exists (
                            select m from ListMembership m
                            where m.slot = s
                              and m.active = true
                              and m.status = com.exivamoeres.domain.MembershipStatus.APPROVED
                          )
                      )
                  ))
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
              and (:vocation is null or (
                    (not exists (
                        select s from TeamSlot s where s.list = l
                      ) and (
                        select count(m) from ListMembership m
                        where m.list = l
                          and m.active = true
                          and m.status = com.exivamoeres.domain.MembershipStatus.APPROVED
                      ) < :maxMembers)
                    or exists (
                        select s from TeamSlot s
                        where s.list = l
                          and (s.vocation is null or s.vocation = :vocation)
                          and not exists (
                            select m from ListMembership m
                            where m.slot = s
                              and m.active = true
                              and m.status = com.exivamoeres.domain.MembershipStatus.APPROVED
                          )
                      )
                  ))
            """)
    Page<HuntingList> search(@Param("world") String world,
                             @Param("creatureId") Long creatureId,
                             @Param("onlyOpenSlots") boolean onlyOpenSlots,
                             @Param("vocation") Vocation vocation,
                             @Param("maxMembers") int maxMembers,
                             Pageable pageable);
    /**
     * "Meus times": onde o usuário é <b>dono</b> ou <b>membro ativo aprovado</b> (item P12).
     *
     * <p>A união era feita em memória, sobre {@code findAllByOwnerId} + todas as
     * participações — sem página e sem teto. <b>Medido</b> numa conta com 12 times: 28
     * consultas, e <b>9 dos 12 itens eram histórico</b> que a tela nem mostra ao abrir (ela
     * abre na aba de ativos). Trazer a união do banco é o que faz a resposta caber tanto no
     * payload quanto no <b>número de consultas</b>: paginar em memória continuaria
     * carregando tudo antes de cortar.</p>
     *
     * <p>O {@code exists} em vez de {@code join} nas participações não é estilo: com
     * {@code join}, quem é dono <b>e</b> membro do mesmo time apareceria duas vezes — era o
     * que a de-duplicação em Java existia para resolver.</p>
     *
     * <p>⚠️ <b>Ordenação total</b> ({@code createdAt desc, id desc}), pelo mesmo motivo da
     * busca pública: sem desempate, dois times criados no mesmo instante trocam de lugar
     * entre uma página e a seguinte, e o "carregar mais" repete um e esconde o outro.</p>
     */
    @Query(value = """
            select l from HuntingList l
            join fetch l.owner
            join fetch l.targetCreature
            where l.status in :statuses
              and (l.owner.id = :userId
                   or exists (
                        select m from ListMembership m
                        where m.list = l
                          and m.user.id = :userId
                          and m.active = true
                          and m.status = com.exivamoeres.domain.MembershipStatus.APPROVED
                      ))
            order by l.createdAt desc, l.id desc
            """,
            countQuery = """
            select count(l) from HuntingList l
            where l.status in :statuses
              and (l.owner.id = :userId
                   or exists (
                        select m from ListMembership m
                        where m.list = l
                          and m.user.id = :userId
                          and m.active = true
                          and m.status = com.exivamoeres.domain.MembershipStatus.APPROVED
                      ))
            """)
    Page<HuntingList> findMine(@Param("userId") Long userId,
                               @Param("statuses") Collection<TeamStatus> statuses,
                               Pageable pageable);
}
