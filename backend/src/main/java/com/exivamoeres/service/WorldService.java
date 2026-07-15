package com.exivamoeres.service;

import java.util.List;

/** Lista de worlds válidos do Tibia, usada pra popular dropdowns na UI. */
public interface WorldService {

    List<String> listWorlds();
}
