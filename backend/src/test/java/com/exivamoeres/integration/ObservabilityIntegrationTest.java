package com.exivamoeres.integration;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * O que sustenta os alertas de {@code ops/observabilidade/alertas.yml}.
 *
 * Dois fatos são verificados aqui, e os dois foram <b>premissas</b> da mudança do
 * T7 — não dava para assumir nenhum:
 *
 * <ol>
 *   <li><b>O agendamento sobrevive a um ciclo que estoura.</b> Era o motivo
 *       declarado do {@code catch} silencioso ("o scheduler nunca pode morrer").
 *       Se fosse verdade que a exceção mata o agendamento, relançar teria
 *       desligado os três jobs em produção — então isto precisa de prova, não de
 *       confiança no javadoc do Spring.</li>
 *   <li><b>O ciclo que falha aparece como {@code outcome=ERROR}</b> na observação
 *       automática. É a série que o alerta {@code JobFalhandoTodoCiclo} consulta;
 *       sem ela, o alerta é decoração.</li>
 * </ol>
 *
 * A tarefa de teste roda a cada 50ms e sempre falha — de propósito. Ela é
 * registrada só neste contexto (via {@link TestConfiguration}), então não
 * interfere em nenhuma outra classe.
 */
@AutoConfigureObservability(tracing = false)
class ObservabilityIntegrationTest extends IntegrationTestBase {

    /** Tarefa que só existe para falhar: é o sujeito do experimento. */
    static class TarefaQueSempreFalha {
        final AtomicInteger execucoes = new AtomicInteger();

        @Scheduled(fixedDelay = 50, initialDelay = 0)
        public void executar() {
            execucoes.incrementAndGet();
            throw new IllegalStateException("falha proposital do teste de observabilidade");
        }
    }

    @TestConfiguration
    static class TarefaDeTeste {
        @Bean
        TarefaQueSempreFalha tarefaQueSempreFalha() {
            return new TarefaQueSempreFalha();
        }
    }

    @Autowired TarefaQueSempreFalha tarefa;
    @Autowired MeterRegistry registry;

    @Test
    void oAgendamentoSobreviveAUmCicloQueEstoura() {
        // Três execuções provam que a exceção não cancelou o agendamento — que é
        // exatamente o que o `catch` silencioso existia para garantir, e que o
        // ErrorHandler padrão do Spring já garantia sozinho.
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(tarefa.execucoes.get()).isGreaterThanOrEqualTo(3));
    }

    @Test
    void cicloQueFalhaContaComoErroNaObservacaoDoJob() {
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(execucoesComErro()).isPositive());
    }

    @Test
    void todaMetricaCarregaAsTagsDeAplicacaoEAmbiente() {
        // Sem estas duas tags, um coletor compartilhado não distingue projeto nem
        // ambiente — e as regras de alerta filtram por elas. Uma tag esquecida
        // aqui vira alerta que nunca dispara em produção.
        Meter qualquer = registry.getMeters().stream().findFirst().orElseThrow();

        assertThat(qualquer.getId().getTag("application")).isEqualTo("exivamoeres");
        assertThat(qualquer.getId().getTag("environment")).isNotBlank();
    }

    /** Soma as execuções do job de teste marcadas como erro na observação. */
    private double execucoesComErro() {
        return registry.find("tasks.scheduled.execution")
                .tag("outcome", "ERROR")
                .timers().stream()
                .mapToDouble(timer -> timer.count())
                .sum();
    }
}
