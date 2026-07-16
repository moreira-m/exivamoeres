package com.exivamoeres.domain.exception;

/**
 * Operação negada: o usuário está autenticado, mas não tem permissão sobre o
 * recurso (ex.: não é o dono do time). Vira 403 — distinta de 401 (sem
 * autenticação) e de 422 (regra de negócio violada).
 */
public class ForbiddenOperationException extends RuntimeException {

    public ForbiddenOperationException(String message) {
        super(message);
    }
}
