package com.exivamoeres.service;

import com.exivamoeres.dto.character.CharacterSummaryResponse;

import java.util.List;

/** Consulta dos personagens de um usuário (já verificados via claim). */
public interface CharacterService {

    List<CharacterSummaryResponse> listMyCharacters(Long userId);
}
