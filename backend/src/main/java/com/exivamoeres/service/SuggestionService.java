package com.exivamoeres.service;

import com.exivamoeres.dto.suggestion.SuggestionResponse;

import java.util.List;

/**
 * Sugestões automáticas de próximos soul cores para um time. Regra: sugerir
 * criaturas cujo core nenhum membro ativo do time desbloqueou ainda
 * (cruzando list_memberships x character_soulcores), priorizando menor
 * difficulty. Gerada quando um membro desbloqueia um core.
 */
public interface SuggestionService {

    /** (Re)gera as sugestões de um time — idempotente (não duplica pendentes). */
    void generateSuggestions(Long listId);

    /** Sugestões não descartadas do time (só membros ativos enxergam). */
    List<SuggestionResponse> listSuggestions(Long userId, Long listId);

    /** Descarta uma sugestão (dismissed = true). Só membros ativos do time. */
    void dismissSuggestion(Long userId, Long suggestionId);
}
