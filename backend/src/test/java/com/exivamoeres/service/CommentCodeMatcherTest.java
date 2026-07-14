package com.exivamoeres.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommentCodeMatcherTest {

    private final CommentCodeMatcher matcher = new CommentCodeMatcher();
    private static final String CODE = "EXIVA-ABC12345";

    @Test
    void encontraCodigoExato() {
        assertThat(matcher.matches("EXIVA-ABC12345", CODE)).isTrue();
    }

    @Test
    void encontraCodigoComEspacosExtras() {
        assertThat(matcher.matches("   EXIVA-ABC12345   ", CODE)).isTrue();
    }

    @Test
    void encontraCodigoComQuebrasDeLinha() {
        assertThat(matcher.matches("meu char principal\nEXIVA-ABC12345\nvendo cores", CODE)).isTrue();
    }

    @Test
    void encontraCodigoEmOutroCase() {
        assertThat(matcher.matches("exiva-abc12345", CODE)).isTrue();
        assertThat(matcher.matches("ExIvA-aBc12345", CODE)).isTrue();
    }

    @Test
    void encontraCodigoNoMeioDeOutroTexto() {
        assertThat(matcher.matches("Recruiting! discord.gg/xyz EXIVA-ABC12345 mando mensagem", CODE)).isTrue();
    }

    @Test
    void naoEncontraEmCommentVazio() {
        assertThat(matcher.matches("", CODE)).isFalse();
    }

    @Test
    void naoEncontraEmCommentNulo() {
        assertThat(matcher.matches(null, CODE)).isFalse();
    }

    @Test
    void naoEncontraCodigoDiferente() {
        assertThat(matcher.matches("EXIVA-OUTRO999", CODE)).isFalse();
    }

    @Test
    void codigoNuloOuVazioNuncaCasa() {
        assertThat(matcher.matches("qualquer coisa", null)).isFalse();
        assertThat(matcher.matches("qualquer coisa", "   ")).isFalse();
    }
}
