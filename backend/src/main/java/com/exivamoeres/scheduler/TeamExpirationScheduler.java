package com.exivamoeres.scheduler;

import com.exivamoeres.service.TeamLifecycleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Arquiva times cujo prazo venceu (app.team.expiration-check-interval).
 *
 * Mesma limitação single-instance do scheduler de claims (documentada em
 * docs/proxima-sessao.md): com múltiplas réplicas, adotar ShedLock. Arquivar é
 * idempotente, então rodar duas vezes é inofensivo.
 */
@Component
@Slf4j
public class TeamExpirationScheduler {

    private final TeamLifecycleService lifecycleService;

    public TeamExpirationScheduler(TeamLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @Scheduled(fixedDelayString = "${app.team.expiration-check-interval}", initialDelayString = "PT1M")
    public void archiveExpiredTeams() {
        try {
            lifecycleService.archiveExpiredTeams();
        } catch (Exception e) {
            log.error("team.expiration.cycle_error error={}", e.toString(), e);
        }
    }
}
