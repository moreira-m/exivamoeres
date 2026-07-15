package com.exivamoeres.repository;

import com.exivamoeres.domain.SoulcoreSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SoulcoreSuggestionRepository extends JpaRepository<SoulcoreSuggestion, Long> {

    List<SoulcoreSuggestion> findAllByListIdAndDismissedFalse(Long listId);

    boolean existsByListIdAndCreatureIdAndDismissedFalse(Long listId, Long creatureId);
}
