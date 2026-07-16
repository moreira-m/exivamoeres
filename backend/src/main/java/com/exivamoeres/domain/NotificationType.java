package com.exivamoeres.domain;

/** Tipo do evento que gerou a notificação. */
public enum NotificationType {
    /** Alguém pediu para entrar no seu time (política MANUAL_APPROVAL). */
    JOIN_REQUEST_RECEIVED,
    /** Seu pedido de entrada foi aceito. */
    JOIN_REQUEST_APPROVED,
    /** Seu pedido de entrada foi recusado. */
    JOIN_REQUEST_REJECTED,
    /** Você foi expulso de um time. */
    KICKED_FROM_TEAM,
    /** Um time do qual você participava foi encerrado. */
    TEAM_DELETED,
    /** Um membro saiu do seu time (notifica o dono). */
    MEMBER_LEFT
}
