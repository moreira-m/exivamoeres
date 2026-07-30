package com.exivamoeres.scheduler;

import com.exivamoeres.logging.LogContext;
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
        // Um id por **ciclo** (`job-xxxxxxxx`) em toda linha do que roda aqui
        // dentro — inclusive nas chamadas à TibiaData, que agora carregam o
        // contexto até os callbacks do Reactor. Sem ele, "quais itens este
        // ciclo tocou?" se responde por horário, misturado com o tráfego HTTP.
        LogContext.runJob(() -> {
            try {
                lifecycleService.archiveExpiredTeams();
            } catch (RuntimeException e) {
                // Loga e **relança**: o agendamento sobrevive de qualquer forma (o
                // ErrorHandler padrão do Spring registra e suprime, mantendo o próximo
                // ciclo), mas engolir a exceção aqui fazia a observação automática do
                // job (`tasks.scheduled.execution`) marcar `outcome=SUCCESS` num ciclo
                // que falhou — ou seja, a métrica mentia justamente no caso que
                // interessa. Ver NEXT_STEPS T7.
                log.error("team.expiration.cycle_error error={}", e.toString());
                throw e;
            }
        });
    }
}
