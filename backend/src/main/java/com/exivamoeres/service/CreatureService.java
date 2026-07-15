package com.exivamoeres.service;

import com.exivamoeres.dto.creature.CreatureResponse;

import java.util.List;

/** Catálogo de criaturas (só leitura) para popular seletores na UI. */
public interface CreatureService {

    List<CreatureResponse> listAll();
}
