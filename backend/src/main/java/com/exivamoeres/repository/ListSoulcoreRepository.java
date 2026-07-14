package com.exivamoeres.repository;

import com.exivamoeres.domain.ListSoulcore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ListSoulcoreRepository extends JpaRepository<ListSoulcore, Long> {

    List<ListSoulcore> findAllByListId(Long listId);

    Optional<ListSoulcore> findByListIdAndCreatureId(Long listId, Long creatureId);
}
