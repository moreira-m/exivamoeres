package com.exivamoeres.domain.exception;

/** Limite de uso estourado (vira 429). */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
