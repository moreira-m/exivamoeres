package com.exivamoeres.repository;

import com.exivamoeres.domain.ListMembership;
import com.exivamoeres.domain.MembershipStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ListMembershipRepository extends JpaRepository<ListMembership, Long> {

    /** Usada na aprovação de claim para desativar memberships do dono anterior. */
    List<ListMembership> findAllByCharacterIdAndActiveTrue(Long characterId);

    List<ListMembership> findAllByListIdAndActiveTrue(Long listId);

    List<ListMembership> findAllByUserIdAndActiveTrue(Long userId);

    /** Membro de fato (conta pra vaga do time). */
    long countByListIdAndActiveTrueAndStatus(Long listId, MembershipStatus status);

    List<ListMembership> findAllByListIdAndStatusAndActiveTrue(Long listId, MembershipStatus status);

    /** Registro único por (list, character) — usado pra reativar em vez de duplicar. */
    Optional<ListMembership> findByListIdAndCharacterId(Long listId, Long characterId);

    /** O personagem é membro ativo e aprovado do time? (autorização de ações no time) */
    Optional<ListMembership> findByListIdAndCharacterIdAndActiveTrueAndStatus(
            Long listId, Long characterId, MembershipStatus status);

    /** O usuário participa ativamente do time por algum personagem? (leitura/chat) */
    boolean existsByListIdAndUserIdAndActiveTrueAndStatus(
            Long listId, Long userId, MembershipStatus status);

    /**
     * Pedidos do usuário nos status pedidos, do mais recente para o mais antigo —
     * a tela "meus pedidos". `APPROVED` não entra na lista de pedidos: quando é
     * aprovado, o time aparece em "meus times".
     */
    List<ListMembership> findAllByUserIdAndStatusInOrderByJoinedAtDesc(
            Long userId, Collection<MembershipStatus> statuses);
}
