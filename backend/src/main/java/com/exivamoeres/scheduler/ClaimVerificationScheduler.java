package com.exivamoeres.scheduler;

import com.exivamoeres.service.ClaimVerificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job de polling: a cada 15 minutos (app.claim.poll-interval) verifica todos
 * os claims PENDING contra a TibiaData API.
 *
 * fixedDelay (não fixedRate): o próximo ciclo só conta a partir do fim do
 * anterior — ciclos nunca se sobrepõem dentro da mesma instância.
 *
 * LIMITAÇÃO CONHECIDA (documentada em docs/proxima-sessao.md): com múltiplas
 * instâncias do backend, cada uma rodaria o job. Hoje o deploy é single
 * instance (Railway); se escalar, adotar ShedLock ou Quartz clusterizado.
 */
@Component
@Slf4j
public class ClaimVerificationScheduler {

    private final ClaimVerificationService verificationService;

    public ClaimVerificationScheduler(ClaimVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @Scheduled(fixedDelayString = "${app.claim.poll-interval}", initialDelayString = "PT1M")
    public void verifyPendingClaims() {
        try {
            verificationService.verifyPendingClaims();
        } catch (Exception e) {
            // O scheduler nunca pode morrer: loga e espera o próximo ciclo.
            log.error("claim.poll.cycle_error error={}", e.toString(), e);
        }
    }
}
