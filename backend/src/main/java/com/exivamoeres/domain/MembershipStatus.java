package com.exivamoeres.domain;

/**
 * Ciclo de vida do pedido de entrada num time.
 * PENDING só existe com join_policy = MANUAL_APPROVAL; AUTO_ACCEPT vai
 * direto para APPROVED.
 */
public enum MembershipStatus {
    PENDING,
    APPROVED,
    REJECTED
}
