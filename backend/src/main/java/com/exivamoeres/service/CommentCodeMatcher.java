package com.exivamoeres.service;

import org.springframework.stereotype.Component;

/**
 * Única fonte de verdade da regra de matching do código de verificação.
 *
 * NUNCA usar igualdade exata: o jogador pode ter outras coisas no comment,
 * colar com espaços/quebras de linha, ou o Tibia.com pode alterar
 * espaçamento. Por isso: trim + lowercase + contains dos dois lados.
 */
@Component
public class CommentCodeMatcher {

    public boolean matches(String comment, String verificationCode) {
        if (comment == null || verificationCode == null) {
            return false;
        }
        String normalizedComment = comment.trim().toLowerCase();
        String normalizedCode = verificationCode.trim().toLowerCase();
        if (normalizedCode.isEmpty()) {
            return false;
        }
        return normalizedComment.contains(normalizedCode);
    }
}
