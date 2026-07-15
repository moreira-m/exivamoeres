package com.exivamoeres.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Gera o código de convite de um time (share_code). Mesmo alfabeto do
 * VerificationCodeGenerator (sem 0/O/1/I), sem prefixo — é um código curto
 * pra compartilhar em link, não precisa ser reconhecível como "de sistema".
 */
@Component
public class ShareCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
