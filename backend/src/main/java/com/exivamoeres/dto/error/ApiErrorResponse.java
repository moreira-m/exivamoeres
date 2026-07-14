package com.exivamoeres.dto.error;

import java.time.Instant;
import java.util.Map;

/** Formato único de erro da API — o frontend só precisa entender um shape. */
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String message,
        Map<String, String> fieldErrors
) {
    public static ApiErrorResponse of(int status, String message) {
        return new ApiErrorResponse(Instant.now(), status, message, null);
    }

    public static ApiErrorResponse withFields(int status, String message, Map<String, String> fields) {
        return new ApiErrorResponse(Instant.now(), status, message, fields);
    }
}
