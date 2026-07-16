package com.exivamoeres.controller;

import com.exivamoeres.domain.exception.BusinessRuleException;
import com.exivamoeres.domain.exception.ExternalServiceException;
import com.exivamoeres.domain.exception.ForbiddenOperationException;
import com.exivamoeres.domain.exception.ResourceNotFoundException;
import com.exivamoeres.dto.error.ApiErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/** Converte exceções em respostas de erro padronizadas (ApiErrorResponse). */
@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleNotFound(ResourceNotFoundException e) {
        return ApiErrorResponse.of(404, e.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ApiErrorResponse handleBusinessRule(BusinessRuleException e) {
        return ApiErrorResponse.of(422, e.getMessage());
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiErrorResponse handleForbidden(ForbiddenOperationException e) {
        return ApiErrorResponse.of(403, e.getMessage());
    }

    @ExceptionHandler(ExternalServiceException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiErrorResponse handleExternalService(ExternalServiceException e) {
        log.warn("api.external_service_error error={}", e.toString());
        return ApiErrorResponse.of(503, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return ApiErrorResponse.withFields(400, "Dados inválidos", fields);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiErrorResponse handleUnexpected(Exception e) {
        // Nunca vazar stacktrace/mensagem interna pro cliente.
        log.error("api.unexpected_error", e);
        return ApiErrorResponse.of(500, "Erro interno inesperado");
    }
}
