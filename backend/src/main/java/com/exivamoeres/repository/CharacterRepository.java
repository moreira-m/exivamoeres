package com.exivamoeres.repository;

import com.exivamoeres.domain.Character;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CharacterRepository extends JpaRepository<Character, Long> {

    Optional<Character> findByNameIgnoreCase(String name);

    List<Character> findAllByOwnerId(Long ownerId);

    /**
     * Personagens candidatos ao refresh periódico de level: retrato local mais
     * velho que {@code threshold} e com pelo menos uma membership APPROVED ativa
     * num time ACTIVE (os que aparecem publicamente pra outros jogadores). Mais
     * antigos primeiro; o {@link Pageable} limita o tamanho do lote.
     */
    @Query("""
            select distinct c from Character c
            where c.updatedAt < :threshold
              and exists (
                select 1 from ListMembership m
                where m.character = c
                  and m.active = true
                  and m.status = com.exivamoeres.domain.MembershipStatus.APPROVED
                  and m.list.status = com.exivamoeres.domain.TeamStatus.ACTIVE
              )
            order by c.updatedAt asc
            """)
    List<Character> findStaleCharactersInActiveTeams(@Param("threshold") Instant threshold, Pageable pageable);
}
