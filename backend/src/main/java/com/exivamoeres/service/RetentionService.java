package com.exivamoeres.service;

/**
 * A limpeza periódica do que já não serve para ninguém (item S8).
 *
 * <p>Os dois métodos são separados de propósito, e <b>cada um em sua transação</b> —
 * mesmo padrão do {@code ClaimTransitionService}. Assim uma falha ao apagar token não
 * desfaz as notificações já apagadas no mesmo ciclo.</p>
 */
public interface RetentionService {

    /** @return quantos refresh tokens vencidos há mais de {@code app.retention.expired-refresh-token-days} sumiram */
    int purgeExpiredRefreshTokens();

    /** @return quantas notificações <b>lidas</b> com mais de {@code app.retention.read-notification-days} sumiram */
    int purgeOldReadNotifications();
}
