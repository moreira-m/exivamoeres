package com.exivamoeres.dto.error;

import java.time.Instant;
import java.util.Map;

/**
 * Formato único de erro da API — o frontend só precisa entender um shape.
 *
 * <p><b>`code` e `params` são o caminho do T2</b>: a tela monta a frase no idioma do
 * usuário a partir do código, e o `message` (sempre em português) fica como <b>reserva</b>
 * para as regras ainda não convertidas e para quem lê o log. Os dois campos são nulos numa
 * recusa legada, e o frontend trata isso.</p>
 */
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String message,
        /** Código estável da recusa, quando existe — ver {@link ErrorCode}. */
        String code,
        /** Valores para a frase da tela (`max`, `character`, `minimum`…). */
        Map<String, String> params,
        Map<String, String> fieldErrors
) {
    public static ApiErrorResponse of(int status, String message) {
        return new ApiErrorResponse(Instant.now(), status, message, null, null, null);
    }

    /** Recusa já convertida: código + valores, e a frase em português como reserva. */
    public static ApiErrorResponse coded(int status, String message, ErrorCode code,
                                        Map<String, String> params) {
        return new ApiErrorResponse(Instant.now(), status, message,
                code == null ? null : code.name(),
                params == null || params.isEmpty() ? null : params,
                null);
    }

    public static ApiErrorResponse withFields(int status, String message, Map<String, String> fields) {
        return new ApiErrorResponse(Instant.now(), status, message, null, null, fields);
    }
}
