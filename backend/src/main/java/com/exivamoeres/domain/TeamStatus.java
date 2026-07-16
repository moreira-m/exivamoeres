package com.exivamoeres.domain;

/**
 * Ciclo de vida de um time de soul core.
 * ACTIVE    — dentro do prazo: aparece na busca pública e aceita escrita
 *             (soulcores e chat).
 * COMPLETED — o core da criatura-alvo foi desbloqueado; vira somente leitura.
 * ARCHIVED  — expirou sem completar; some da busca pública e vira somente
 *             leitura (o dono pode renovar se tiver vaga no plano).
 * CLOSED    — encerrado manualmente pelo dono; some da busca e vira somente
 *             leitura, mas NÃO é renovável (decisão explícita do criador).
 */
public enum TeamStatus {
    ACTIVE,
    COMPLETED,
    ARCHIVED,
    CLOSED;

    /** Escrita (marcar soulcore, enviar mensagem) só é permitida em times ativos. */
    public boolean allowsWrites() {
        return this == ACTIVE;
    }
}
