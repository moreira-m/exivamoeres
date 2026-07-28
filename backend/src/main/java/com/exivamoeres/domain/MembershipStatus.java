package com.exivamoeres.domain;

/**
 * Ciclo de vida do pedido de entrada num time.
 * PENDING só existe com join_policy = MANUAL_APPROVAL; AUTO_ACCEPT vai
 * direto para APPROVED.
 */
public enum MembershipStatus {
    PENDING,
    APPROVED,
    /** O <b>dono</b> recusou o pedido. */
    REJECTED,
    /**
     * O <b>solicitante</b> desistiu do próprio pedido.
     *
     * Status separado de REJECTED de propósito: quem decidiu é outra pessoa, e o
     * histórico do usuário não pode dizer que ele foi recusado quando ele
     * simplesmente mudou de ideia.
     */
    CANCELLED
}
