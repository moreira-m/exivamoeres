package com.exivamoeres.repository;

import com.exivamoeres.domain.ListMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ListMembershipRepository extends JpaRepository<ListMembership, Long> {

    /** Usada na aprovação de claim para desativar memberships do dono anterior. */
    List<ListMembership> findAllByCharacterIdAndActiveTrue(Long characterId);

    List<ListMembership> findAllByListIdAndActiveTrue(Long listId);

    List<ListMembership> findAllByUserIdAndActiveTrue(Long userId);
}
