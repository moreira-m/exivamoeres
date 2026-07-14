package com.exivamoeres.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class VerificationCodeGeneratorTest {

    private final VerificationCodeGenerator generator = new VerificationCodeGenerator();

    @Test
    void geraCodigoNoFormatoEsperado() {
        String code = generator.generate();
        assertThat(code).matches("EXIVA-[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}");
    }

    @Test
    void naoGeraCodigosRepetidosEmSequencia() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            codes.add(generator.generate());
        }
        assertThat(codes).hasSize(1000);
    }
}
