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
    MEMBER_LEFT,
    /**
     * O dono mudou o horário da caçada (notifica os membros aprovados).
     * É o campo pelo qual as pessoas escolhem o time — mudá-lo em silêncio faz
     * quem entrou aparecer na hora errada.
     */
    TEAM_SCHEDULE_CHANGED,
    /** O dono mudou o level mínimo do time (notifica os membros aprovados). */
    TEAM_MINIMUM_LEVEL_CHANGED,
    /**
     * O time subiu o level mínimo **acima** do personagem de um pedido pendente:
     * o pedido deixou de poder ser aprovado (notifica quem pediu).
     *
     * Tipo próprio, e não reuso do {@link #TEAM_MINIMUM_LEVEL_CHANGED}, porque o
     * destinatário é outro: para o membro a notícia é "o time mudou"; para quem
     * pediu é "**seu pedido** pode não ser aprovado".
     */
    JOIN_REQUEST_AT_RISK
}
