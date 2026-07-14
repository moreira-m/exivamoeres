package com.exivamoeres.domain.exception;

/** Violação de regra de negócio (vira 422). */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
