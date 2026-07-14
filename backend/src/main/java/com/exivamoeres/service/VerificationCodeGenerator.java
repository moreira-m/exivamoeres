package com.exivamoeres.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Gera o código que o jogador cola no campo Comment do Tibia.com.
 * Formato: EXIVA-XXXXXXXX (8 chars alfanuméricos, sem 0/O/1/I pra evitar
 * confusão ao digitar). O prefixo "EXIVA-" torna o código reconhecível e
 * praticamente elimina falso positivo do contains() num comment qualquer.
 */
@Component
public class VerificationCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final String PREFIX = "EXIVA-";

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder code = new StringBuilder(PREFIX);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
