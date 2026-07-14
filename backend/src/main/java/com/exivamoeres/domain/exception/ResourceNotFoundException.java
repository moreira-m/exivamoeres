package com.exivamoeres.domain.exception;

/** Recurso inexistente ou não visível para o usuário autenticado (vira 404). */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
