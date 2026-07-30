package com.exivamoeres.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * As duas operações de correlação que não são HTTP (item T15) — unitárias de propósito:
 * o que precisa de prova é o comportamento do MDC, e nada disso depende de banco.
 *
 * <p>O tema de todos os casos é o mesmo do {@code RequestIdFilterTest}: <b>o que fica
 * depois</b>. Thread volta para o pool, e id vazado marca a próxima coisa com o id da
 * anterior — log com id errado é pior que log sem id, porque parece confiável.</p>
 */
class LogContextTest {

    @AfterEach
    void limparMdc() {
        MDC.clear();
    }

    @Test
    void oCicloDeJobRodaComUmIdProprio() {
        AtomicReference<String> durante = new AtomicReference<>();

        LogContext.runJob(() -> durante.set(MDC.get(LogContext.JOB_RUN_ID)));

        // O prefixo é o que deixa a origem legível na linha de log sem consultar a chave.
        assertThat(durante.get()).startsWith("job-");
        assertThat(MDC.get(LogContext.JOB_RUN_ID)).isNull();
    }

    @Test
    void doisCiclosTemIdsDiferentes() {
        AtomicReference<String> primeiro = new AtomicReference<>();
        AtomicReference<String> segundo = new AtomicReference<>();

        LogContext.runJob(() -> primeiro.set(MDC.get(LogContext.JOB_RUN_ID)));
        LogContext.runJob(() -> segundo.set(MDC.get(LogContext.JOB_RUN_ID)));

        // É o que permite responder "quais linhas são deste ciclo?" — se o id fosse fixo,
        // a pergunta continuaria valendo horário.
        assertThat(primeiro.get()).isNotEqualTo(segundo.get());
    }

    @Test
    void oIdDoJobSaiAteQuandoOCicloEstoura() {
        assertThatThrownBy(() -> LogContext.runJob(() -> {
            throw new IllegalStateException("ciclo falhou");
        })).isInstanceOf(IllegalStateException.class);

        // O `finally` existe para isto: o ciclo que estoura é justamente o que deixaria o
        // id sujo para o próximo — e é o que se vai investigar.
        assertThat(MDC.get(LogContext.JOB_RUN_ID)).isNull();
    }

    @Test
    void aExcecaoDoCicloPassaDireto() {
        // Os três jobs relançam de propósito, para `tasks.scheduled.execution` marcar
        // outcome=ERROR. Engolir aqui faria a métrica mentir de novo (ver T7).
        assertThatThrownBy(() -> LogContext.runJob(() -> {
            throw new IllegalStateException("falha do ciclo");
        })).hasMessage("falha do ciclo");
    }

    @Test
    void oContextoCapturadoValeNaOutraThread() throws Exception {
        MDC.put("requestId", "req-da-tela");
        Map<String, String> contexto = LogContext.capture();
        AtomicReference<String> naOutraThread = new AtomicReference<>();

        // Simula o callback do Reactor: outra thread, MDC vazio.
        Thread outra = new Thread(() -> LogContext.with(contexto,
                () -> naOutraThread.set(MDC.get("requestId"))));
        outra.start();
        outra.join();

        assertThat(naOutraThread.get()).isEqualTo("req-da-tela");
    }

    @Test
    void semContextoCapturado_aAcaoRodaIgual() {
        AtomicReference<Boolean> rodou = new AtomicReference<>(false);

        LogContext.with(null, () -> rodou.set(true));
        LogContext.with(Map.of(), () -> rodou.set(true));

        // Boot, teste, qualquer coisa sem correlação: a ação não pode depender de haver
        // contexto — a linha sai sem id, que é o comportamento certo.
        assertThat(rodou.get()).isTrue();
    }

    @Test
    void oContextoAnteriorDaThreadEhRestaurado() {
        MDC.put("requestId", "do-dono-da-thread");

        LogContext.with(Map.of("requestId", "emprestado"), () -> {
            assertThat(MDC.get("requestId")).isEqualTo("emprestado");
        });

        // Restaurar, e não limpar: a thread do Reactor é compartilhada e pode estar
        // servindo outra coisa entre um callback e outro.
        assertThat(MDC.get("requestId")).isEqualTo("do-dono-da-thread");
    }

    @Test
    void aThreadSemContextoProprioFicaLimpaDepois() {
        LogContext.with(Map.of("requestId", "emprestado"), () -> {
            assertThat(MDC.get("requestId")).isEqualTo("emprestado");
        });

        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void oContextoSaiAteQuandoAAcaoEstoura() {
        MDC.put("requestId", "do-dono-da-thread");

        assertThatThrownBy(() -> LogContext.with(Map.of("requestId", "emprestado"), () -> {
            throw new IllegalStateException("log falhou");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(MDC.get("requestId")).isEqualTo("do-dono-da-thread");
    }
}
