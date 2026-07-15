package com.exivamoeres.service;

import com.exivamoeres.domain.HuntingList;

/**
 * Transições AUTOMÁTICAS de status de um time (as disparadas pelo sistema, não
 * pelo usuário). Isola a máquina de estados do ciclo de vida para não espalhar
 * essa lógica pelos services de soulcore/scheduler.
 *
 * As transições iniciadas pelo usuário (criar com prazo, renovar) vivem em
 * HuntingListService, pois dependem dos limites de plano que ele já governa.
 */
public interface TeamLifecycleService {

    /**
     * Se a criatura desbloqueada é o alvo do time, marca o time como COMPLETED
     * (missão cumprida → vira somente leitura).
     */
    void completeIfTargetUnlocked(HuntingList list, Long unlockedCreatureId);

    /**
     * Arquiva todos os times ATIVOS cujo prazo já venceu. Chamado pelo job de
     * expiração.
     *
     * @return quantos times foram arquivados
     */
    int archiveExpiredTeams();
}
