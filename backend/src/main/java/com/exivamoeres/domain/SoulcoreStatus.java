package com.exivamoeres.domain;

/**
 * Estado de um soul core dentro de uma lista de caça:
 * OBTAINED — alguém do grupo lootou o core, mas ele ainda não foi gasto;
 * UNLOCKED — o core foi usado no Soulpit e o Animus Mastery foi desbloqueado.
 */
public enum SoulcoreStatus {
    OBTAINED,
    UNLOCKED
}
