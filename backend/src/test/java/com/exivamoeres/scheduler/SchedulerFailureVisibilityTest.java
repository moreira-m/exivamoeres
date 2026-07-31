package com.exivamoeres.scheduler;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.exivamoeres.logging.LogContext;
import com.exivamoeres.service.CharacterLevelRefreshService;
import com.exivamoeres.service.ClaimVerificationService;
import com.exivamoeres.service.RetentionService;
import com.exivamoeres.service.TeamLifecycleService;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * Um ciclo de job que falha tem que <b>falhar visivelmente</b>.
 *
 * Os schedulers engoliam a exceção ({@code catch → log.error}, sem relançar)
 * com a intenção de "o scheduler nunca pode morrer". O efeito colateral era o
 * pior possível para quem opera: a observação automática do Spring
 * ({@code tasks.scheduled.execution}) marcava {@code outcome=SUCCESS} num ciclo
 * que falhou, então a métrica ficava saudável enquanto claim nenhum era
 * verificado. O único vestígio era uma linha de log que ninguém lê.
 *
 * Estes testes são o guarda-corpo contra alguém reintroduzir o {@code catch}
 * silencioso — são unitários de propósito: a regra é "propaga", e provar isso não
 * precisa de banco.
 *
 * Que o agendamento <b>sobrevive</b> à exceção é a outra metade, provada em
 * {@code ObservabilityIntegrationTest} com um Spring de verdade.
 */
class SchedulerFailureVisibilityTest {

    private static final RuntimeException FALHA = new IllegalStateException("banco fora");

    @Test
    void falhaAoVerificarClaimsPropaga() {
        ClaimVerificationService servico = mock(ClaimVerificationService.class);
        doThrow(FALHA).when(servico).verifyPendingClaims();

        assertThatThrownBy(() -> new ClaimVerificationScheduler(servico).verifyPendingClaims())
                .isSameAs(FALHA);
    }

    @Test
    void falhaAoArquivarTimesPropaga() {
        TeamLifecycleService servico = mock(TeamLifecycleService.class);
        doThrow(FALHA).when(servico).archiveExpiredTeams();

        assertThatThrownBy(() -> new TeamExpirationScheduler(servico).archiveExpiredTeams())
                .isSameAs(FALHA);
    }

    @Test
    void falhaAoAtualizarLevelsPropaga() {
        CharacterLevelRefreshService servico = mock(CharacterLevelRefreshService.class);
        doThrow(FALHA).when(servico).refreshStaleTeamCharacters();

        assertThatThrownBy(() -> new CharacterLevelRefreshScheduler(servico).refreshLevels())
                .isSameAs(FALHA);
    }

    @Test
    void falhaAoLimparTabelasPropaga() {
        // O job mais silencioso dos quatro (S8): ninguém abre chamado porque uma tabela
        // está grande, então a métrica é o único aviso que existe.
        RetentionService servico = mock(RetentionService.class);
        doThrow(FALHA).when(servico).purgeExpiredRefreshTokens();

        assertThatThrownBy(() -> new RetentionScheduler(servico).purgeExpiredData())
                .isSameAs(FALHA);
    }

    @Test
    void oCicloAbreUmIdDeCorrelacaoParaAsLinhasDele() {
        // O que este teste guarda (item T15): sem o `runJob`, as linhas do ciclo saem com
        // o marcador vazio e se misturam com o tráfego HTTP concorrente — "quais itens
        // este ciclo tocou?" volta a se responder por horário.
        AtomicReference<String> idDuranteOCiclo = new AtomicReference<>();
        ClaimVerificationService servico = mock(ClaimVerificationService.class);
        doAnswer(chamada -> {
            idDuranteOCiclo.set(MDC.get(LogContext.JOB_RUN_ID));
            return null;
        }).when(servico).verifyPendingClaims();

        new ClaimVerificationScheduler(servico).verifyPendingClaims();

        assertThat(idDuranteOCiclo.get()).startsWith("job-");
        // E some no fim: a thread volta para o pool do agendador.
        assertThat(MDC.get(LogContext.JOB_RUN_ID)).isNull();
    }

    @Test
    void aLinhaDeErroDoCicloSaiComOIdDoCiclo() {
        Logger logger = (Logger) LoggerFactory.getLogger(ClaimVerificationScheduler.class);
        ListAppender<ILoggingEvent> linhas = new ListAppender<>();
        linhas.start();
        logger.addAppender(linhas);
        ClaimVerificationService servico = mock(ClaimVerificationService.class);
        doThrow(FALHA).when(servico).verifyPendingClaims();

        try {
            new ClaimVerificationScheduler(servico).verifyPendingClaims();
        } catch (RuntimeException esperada) {
            // Relançar é o assunto dos outros testes desta classe.
        } finally {
            logger.detachAppender(linhas);
        }

        // É a linha que se procura quando um ciclo falhou: ela precisa levar até as
        // outras linhas do **mesmo** ciclo.
        assertThat(linhas.list)
                .filteredOn(e -> e.getMessage().startsWith("claim.poll.cycle_error"))
                .singleElement()
                .satisfies(e -> assertThat(e.getMDCPropertyMap().get(LogContext.JOB_RUN_ID))
                        .startsWith("job-"));
    }

    @Test
    void cicloSemErroNaoLancaNada() {
        // O contrário do resto da classe: relançar não pode virar "sempre estoura".
        TeamLifecycleService servico = mock(TeamLifecycleService.class);

        assertThatCode(() -> new TeamExpirationScheduler(servico).archiveExpiredTeams())
                .doesNotThrowAnyException();
    }
}
