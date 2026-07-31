package com.exivamoeres.scheduler;

import com.exivamoeres.logging.LogContext;
import com.exivamoeres.service.RetentionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Limpa diariamente o que já não serve para ninguém (item S8): refresh token vencido
 * há muito tempo e notificação lida antiga.
 *
 * <p>{@code fixedDelay} com {@code initialDelay} curto, em vez de um {@code cron} às 3h:
 * o job apaga uma fatia pequena com índice (medido em milissegundos na V21), então
 * horário de baixa não importa — e {@code cron} teria o efeito ruim de <b>pular o dia</b>
 * sempre que um deploy caísse na hora marcada. Com {@code fixedDelay}, deploy frequente
 * só faz o job rodar <b>mais</b> vezes, o que é inofensivo: apagar o que já sumiu apaga
 * zero linhas.</p>
 *
 * <p>Mesma limitação single-instance dos outros três jobs: com réplicas, adotar ShedLock.
 * Duas instâncias limpando ao mesmo tempo é inofensivo (a segunda não acha nada).</p>
 */
@Component
@Slf4j
public class RetentionScheduler {

    private final RetentionService retentionService;

    public RetentionScheduler(RetentionService retentionService) {
        this.retentionService = retentionService;
    }

    @Scheduled(fixedDelayString = "${app.retention.interval}", initialDelayString = "PT5M")
    public void purgeExpiredData() {
        // Um id por ciclo (`job-xxxxxxxx`) em toda linha do que roda aqui dentro — é o
        // que liga "apagou 4.212 tokens" a "apagou 130 notificações" como sendo a mesma
        // passada, em vez de duas linhas soltas de horário parecido.
        LogContext.runJob(() -> {
            try {
                // Cada purga tem transação própria (ver RetentionService): se a segunda
                // estourar, o que a primeira já apagou continua apagado.
                retentionService.purgeExpiredRefreshTokens();
                retentionService.purgeOldReadNotifications();
            } catch (RuntimeException e) {
                // Loga e **relança**, como os outros três: engolir faria
                // `tasks.scheduled.execution` marcar outcome=SUCCESS num ciclo que
                // falhou, e este job é invisível por natureza — ninguém reclama de uma
                // tabela crescendo. Ver NEXT_STEPS T7.
                log.error("retention.cycle_error error={}", e.toString());
                throw e;
            }
        });
    }
}
