package com.exivamoeres.service;

/**
 * Motor de verificação dos claims — usado tanto pelo job de polling quanto
 * pelo endpoint verify-now, garantindo uma única regra de decisão.
 */
public interface ClaimVerificationService {

    /** Varre todos os claims PENDING (chamado pelo scheduler a cada 15 min). */
    void verifyPendingClaims();

    /**
     * Verifica um único claim contra a TibiaData.
     *
     * @return o resultado da checagem (o estado do claim já foi persistido)
     */
    VerificationOutcome verifyClaim(Long claimId);

    enum VerificationOutcome {
        /** Código encontrado no comment — claim aprovado e posse transferida. */
        APPROVED,
        /** Resposta válida, mas código ausente — claim segue pendente. */
        CODE_NOT_FOUND,
        /** Claim passou das 24h e foi rejeitado. */
        EXPIRED,
        /** TibiaData indisponível após retries — nada foi alterado. */
        UNREACHABLE
    }
}
