package com.exivamoeres.service;

import com.exivamoeres.domain.Plan;

import java.time.Duration;

/**
 * Traduz um plano de conta nos seus limites de negócio. Ponto único de
 * decisão: qualquer regra que dependa do plano (quantos times, por quanto
 * tempo) consulta esta abstração, nunca compara o enum na mão.
 */
public interface PlanPolicy {

    /** Máximo de times ATIVOS simultâneos que o plano permite criar. */
    int maxActiveTeams(Plan plan);

    /** Por quanto tempo um time recém-criado/renovado fica ativo. */
    Duration teamDuration(Plan plan);
}
