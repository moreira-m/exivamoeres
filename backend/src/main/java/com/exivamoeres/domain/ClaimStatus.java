package com.exivamoeres.domain;

/**
 * Ciclo de vida de um claim de personagem:
 * PENDING -> APPROVED (código encontrado no comment do Tibia.com)
 * PENDING -> REJECTED (expirou após 24h sem verificação, ou cancelado)
 */
public enum ClaimStatus {
    PENDING,
    APPROVED,
    REJECTED
}
