package com.exivamoeres.service;

/**
 * ESQUELETO PARA A SESSÃO 2 — sugestões automáticas de soul cores.
 * Ideia central: sugerir criaturas cujo core NENHUM membro ativo da lista
 * desbloqueou ainda (cruzando list_memberships x character_soulcores),
 * priorizando menor difficulty. Gerar via job agendado ou sob demanda.
 */
public interface SuggestionService {

    /** TODO(sessão 2): (re)gerar sugestões para uma lista. */
    void generateSuggestions(Long listId);

    /** TODO(sessão 2): sugestões não descartadas da lista. */
    Object listSuggestions(Long userId, Long listId);

    /** TODO(sessão 2): descartar sugestão (dismissed = true). */
    void dismissSuggestion(Long userId, Long suggestionId);
}
