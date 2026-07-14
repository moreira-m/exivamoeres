package com.exivamoeres.domain.exception;

/**
 * Falha ao consultar um serviço externo (TibiaData) depois de esgotados os
 * retries. Diferenciada das demais para que o scheduler trate falha de rede
 * como "não checado" — nunca como reprovação do claim.
 */
public class ExternalServiceException extends RuntimeException {

    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public ExternalServiceException(String message) {
        super(message);
    }
}
