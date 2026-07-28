package com.exivamoeres.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parser da vocação que vem da TibiaData.
 *
 * Mesma filosofia do `CommentCodeMatcher`: dado externo **nunca** é comparado por
 * igualdade exata. O que importa é o papel no grupo (base), não a promoção.
 */
class VocationTest {

    @Test
    void mapeiaAsVocacoesBase() {
        assertThat(Vocation.fromTibiaData("Knight")).isEqualTo(Vocation.KNIGHT);
        assertThat(Vocation.fromTibiaData("Paladin")).isEqualTo(Vocation.PALADIN);
        assertThat(Vocation.fromTibiaData("Sorcerer")).isEqualTo(Vocation.SORCERER);
        assertThat(Vocation.fromTibiaData("Druid")).isEqualTo(Vocation.DRUID);
        assertThat(Vocation.fromTibiaData("Monk")).isEqualTo(Vocation.MONK);
    }

    @Test
    void promovidoCaiNaMesmaVocacaoBase() {
        // Um Elder Druid e um Druid ocupam o mesmo papel no grupo; o que separa os
        // dois na prática é o level, que já tem regra própria (minimum_level).
        assertThat(Vocation.fromTibiaData("Elite Knight")).isEqualTo(Vocation.KNIGHT);
        assertThat(Vocation.fromTibiaData("Royal Paladin")).isEqualTo(Vocation.PALADIN);
        assertThat(Vocation.fromTibiaData("Master Sorcerer")).isEqualTo(Vocation.SORCERER);
        assertThat(Vocation.fromTibiaData("Elder Druid")).isEqualTo(Vocation.DRUID);
        assertThat(Vocation.fromTibiaData("Exalted Monk")).isEqualTo(Vocation.MONK);
    }

    @Test
    void toleraCaseEEspacos() {
        assertThat(Vocation.fromTibiaData("  elder druid  ")).isEqualTo(Vocation.DRUID);
        assertThat(Vocation.fromTibiaData("ELITE KNIGHT")).isEqualTo(Vocation.KNIGHT);
    }

    @Test
    void semVocacaoOuDesconhecidaViraNone() {
        assertThat(Vocation.fromTibiaData("None")).isEqualTo(Vocation.NONE);
        assertThat(Vocation.fromTibiaData(null)).isEqualTo(Vocation.NONE);
        assertThat(Vocation.fromTibiaData("  ")).isEqualTo(Vocation.NONE);
        // Vocação nova no jogo não pode derrubar o site: cai em NONE e, no pior
        // caso, deixa de preencher vaga restrita até alguém acrescentar o valor.
        assertThat(Vocation.fromTibiaData("Necromancer")).isEqualTo(Vocation.NONE);
    }

    @Test
    void vagaLivreAceitaQualquerVocacao() {
        assertThat(Vocation.KNIGHT.fits(null)).isTrue();
        assertThat(Vocation.NONE.fits(null)).isTrue();
    }

    @Test
    void vagaComExigenciaSoAceitaAVocacaoExigida() {
        assertThat(Vocation.KNIGHT.fits(Vocation.KNIGHT)).isTrue();
        assertThat(Vocation.DRUID.fits(Vocation.KNIGHT)).isFalse();
        // Personagem sem vocação não preenche vaga restrita.
        assertThat(Vocation.NONE.fits(Vocation.KNIGHT)).isFalse();
    }
}
