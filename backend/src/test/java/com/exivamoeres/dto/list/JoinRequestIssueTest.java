package com.exivamoeres.dto.list;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A classificação dos motivos e a regra de "o que a pessoa deve fazer mudou" (item P28).
 *
 * <p><b>Por que unitário, e não só pelo serviço.</b> Só duas trocas de motivo são
 * alcançáveis por uma edição de time — {@code BELOW_MINIMUM_LEVEL ↔
 * VOCATION_NOT_IN_COMPOSITION} —, porque o world de um personagem não é editável pelo dono.
 * As outras combinações existem na regra e <b>não têm caminho</b> até elas pelo produto:
 * medido com mutação, apagar metade da condição não reprovava nenhum teste de integração.
 * Aqui a matriz inteira é expressável, então a regra fica coberta onde ela é escrita.</p>
 */
class JoinRequestIssueTest {

    @Test
    void osDoisMotivosSemConserto() {
        // O que sustenta a ordem de precedência do enum: primeiro o que a pessoa não pode
        // resolver, porque mostrar o consertável no lugar faz gastar tempo em vão.
        assertThat(JoinRequestIssue.WORLD_MISMATCH.isDefinitive()).isTrue();
        assertThat(JoinRequestIssue.VOCATION_NOT_IN_COMPOSITION.isDefinitive()).isTrue();
    }

    @Test
    void levelMinimoEhOUnicoQueOJogadorResolve() {
        assertThat(JoinRequestIssue.BELOW_MINIMUM_LEVEL.isDefinitive()).isFalse();
    }

    @ParameterizedTest(name = "{0} -> {1} avisa? {2}")
    @CsvSource({
            // A única troca que muda a ação: de "jogue mais" para "use outro personagem".
            "BELOW_MINIMUM_LEVEL,         VOCATION_NOT_IN_COMPOSITION, true",
            "BELOW_MINIMUM_LEVEL,         WORLD_MISMATCH,              true",
            // Definitivo -> consertável: boa notícia **parcial**, continua inaprovável.
            "VOCATION_NOT_IN_COMPOSITION, BELOW_MINIMUM_LEVEL,         false",
            "WORLD_MISMATCH,              BELOW_MINIMUM_LEVEL,         false",
            // Entre dois definitivos a conclusão é a mesma: nada a fazer, de qualquer jeito.
            "VOCATION_NOT_IN_COMPOSITION, WORLD_MISMATCH,              false",
            "WORLD_MISMATCH,              VOCATION_NOT_IN_COMPOSITION, false",
    })
    void aTrocaDeMotivoSoAvisaQuandoAAcaoMuda(JoinRequestIssue antes, JoinRequestIssue agora,
                                              boolean esperado) {
        assertThat(JoinRequestIssue.actionChanged(antes, agora)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @EnumSource(JoinRequestIssue.class)
    void motivoQueNaoMudouNuncaAvisa(JoinRequestIssue motivo) {
        // Sai de graça da condição (`x && !x`), e é o recorte de transição da família
        // inteira: quem já estava fora pelo mesmo motivo não recebe o aviso de novo a cada
        // reconfiguração do dono.
        assertThat(JoinRequestIssue.actionChanged(motivo, motivo)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(JoinRequestIssue.class)
    void cabiaOuVoltouACaberNaoEhEsteRamo(JoinRequestIssue motivo) {
        // Nulo é "o pedido cabe". Os dois lados são tratados pelos outros ramos da
        // transição (deixou de caber / voltou a caber), e este não pode roubá-los.
        assertThat(JoinRequestIssue.actionChanged(null, motivo)).isFalse();
        assertThat(JoinRequestIssue.actionChanged(motivo, null)).isFalse();
        assertThat(JoinRequestIssue.actionChanged(null, null)).isFalse();
    }
}
