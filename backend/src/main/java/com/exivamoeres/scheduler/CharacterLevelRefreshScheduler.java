package com.exivamoeres.scheduler;

import com.exivamoeres.service.CharacterLevelRefreshService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job que atualiza o level exibido dos personagens em times ativos
 * (app.character.level-refresh-interval), rebuscando na TibiaData só quem está
 * com o retrato vencido — ver {@link CharacterLevelRefreshService}.
 *
 * fixedDelay: o próximo ciclo só conta a partir do fim do anterior, então a
 * pausa entre chamadas dentro do lote nunca causa sobreposição.
 *
 * Mesma limitação single-instance dos outros schedulers (documentada em
 * docs/proxima-sessao.md): com múltiplas réplicas, adotar ShedLock. Re-sync é
 * idempotente, então rodar duas vezes é inofensivo (só gasta chamadas a mais).
 */
@Component
@Slf4j
public class CharacterLevelRefreshScheduler {

    private final CharacterLevelRefreshService refreshService;

    public CharacterLevelRefreshScheduler(CharacterLevelRefreshService refreshService) {
        this.refreshService = refreshService;
    }

    @Scheduled(fixedDelayString = "${app.character.level-refresh-interval}", initialDelayString = "PT2M")
    public void refreshLevels() {
        try {
            refreshService.refreshStaleTeamCharacters();
        } catch (RuntimeException e) {
            // Loga e **relança**: o agendamento sobrevive de qualquer forma (o
            // ErrorHandler padrão do Spring registra e suprime, mantendo o próximo
            // ciclo), mas engolir a exceção aqui fazia a observação automática do
            // job (`tasks.scheduled.execution`) marcar `outcome=SUCCESS` num ciclo
            // que falhou — ou seja, a métrica mentia justamente no caso que
            // interessa. Ver NEXT_STEPS T7.
            log.error("character.level_refresh.cycle_error error={}", e.toString());
            throw e;
        }
    }
}
