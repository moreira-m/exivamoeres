package com.exivamoeres.scheduler;

import com.exivamoeres.service.CharacterLevelRefreshService;
import com.exivamoeres.service.ClaimVerificationService;
import com.exivamoeres.service.TeamLifecycleService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * Um ciclo de job que falha tem que <b>falhar visivelmente</b>.
 *
 * Os três schedulers engoliam a exceção ({@code catch → log.error}, sem relançar)
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
    void cicloSemErroNaoLancaNada() {
        // O contrário do resto da classe: relançar não pode virar "sempre estoura".
        TeamLifecycleService servico = mock(TeamLifecycleService.class);

        assertThatCode(() -> new TeamExpirationScheduler(servico).archiveExpiredTeams())
                .doesNotThrowAnyException();
    }
}
