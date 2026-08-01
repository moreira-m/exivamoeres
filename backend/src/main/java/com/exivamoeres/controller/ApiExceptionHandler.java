package com.exivamoeres.controller;

import com.exivamoeres.domain.exception.BusinessRuleException;
import com.exivamoeres.domain.exception.ExternalServiceException;
import com.exivamoeres.domain.exception.ForbiddenOperationException;
import com.exivamoeres.domain.exception.InvalidCredentialsException;
import com.exivamoeres.domain.exception.ResourceNotFoundException;
import com.exivamoeres.domain.exception.TooManyRequestsException;
import com.exivamoeres.dto.error.ApiErrorResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
        // `code`/`params` quando a regra já foi convertida (T2); a frase em português vai
        // sempre, como reserva para as que ainda não foram e para o log.
        return ApiErrorResponse.coded(422, e.getMessage(), e.getCode(), e.getParams());
    }

    /**
     * Credencial que não vale (senha, refresh token). **401**, não 422: quem consome
     * a API precisa distinguir "autentique de novo" de "corrija o payload".
     *
     * Sem `WWW-Authenticate` de propósito: o header faria alguns navegadores abrirem
     * o diálogo de basic auth, e esta API é Bearer — o cliente já sabe o que fazer.
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiErrorResponse handleInvalidCredentials(InvalidCredentialsException e) {
        return ApiErrorResponse.of(401, e.getMessage());
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiErrorResponse handleForbidden(ForbiddenOperationException e) {
        return ApiErrorResponse.of(403, e.getMessage());
    }

    @ExceptionHandler(TooManyRequestsException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ApiErrorResponse handleTooManyRequests(TooManyRequestsException e) {
        return ApiErrorResponse.of(429, e.getMessage());
    }

    @ExceptionHandler(ExternalServiceException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiErrorResponse handleExternalService(ExternalServiceException e) {
        log.warn("api.external_service_error error={}", e.toString());
        return ApiErrorResponse.of(503, e.getMessage());
    }

    /**
     * Circuito aberto = a TibiaData está fora e a chamada nem foi tentada.
     *
     * ⚠️ Sem este handler a exceção caía no galho genérico e virava <b>500</b> — medido no
     * item S3. Três coisas erradas nisso, e a terceira é a pior: a tela dizia "erro
     * inesperado" quando o certo era "indisponível, tente daqui a pouco"; o
     * {@code Retry-After} não existia; e o 500 <b>envenenava o alerta de taxa de 5xx</b>,
     * que é o único crítico que significa "o site está quebrado por um bug". Uma queda da
     * TibiaData acordava alguém com o diagnóstico errado.
     *
     * É a mesma regra do S11 e do S13: falha de terceiro é 503, não 500.
     */
    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ApiErrorResponse> handleCircuitOpen(CallNotPermittedException e) {
        // `getCausingCircuitBreakerName` diz **qual** fluxo está fora (interactive,
        // background, worlds) — é o que separa "o produto travou" de "um job degradou".
        log.warn("api.circuit_open circuit={}", e.getCausingCircuitBreakerName());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                // Retry-After em segundos, casado com wait-duration-in-open-state: em vez
                // de o cliente tentar de novo na hora (e tomar 503 igual), ele sabe quanto
                // esperar. É o cabeçalho padrão para 503.
                .header(HttpHeaders.RETRY_AFTER, "60")
                .body(ApiErrorResponse.of(503,
                        "Serviço temporariamente indisponível. Tente novamente em alguns minutos."));
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

    // ---------------------------------------------------------------------
    // Erros de REQUISIÇÃO (4xx) que o Spring lança antes de o controller rodar.
    //
    // Sem os handlers abaixo eles caíam no galho genérico e viravam **500** —
    // mentindo sobre a culpa ("o servidor quebrou" quando o cliente mandou lixo) e
    // envenenando o alerta de taxa de 5xx: um crawler passando `?page=abc` virava
    // incidente. Ver NEXT_STEPS S11.
    //
    // Regra comum a todos: **nunca** devolver a mensagem do Spring. Ela carrega
    // nome de classe interna e assinatura de método — por exemplo
    // "Required request body is missing: public com.exivamoeres...AuthController.login(...)".
    // ---------------------------------------------------------------------

    /**
     * Parâmetro que o Spring não conseguiu converter: `?vocation=BANANA`,
     * `?creatureId=abc`, `?page=abc` e também `@PathVariable` (`/api/lists/abc`).
     * Tudo isso é a mesma exceção.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        logClientError("api.invalid_parameter name={}", e.getName());
        return ApiErrorResponse.withFields(400, "Parâmetro inválido",
                Map.of(e.getName(), expectativa(e.getRequiredType())));
    }

    /** Parâmetro obrigatório ausente. Nenhum endpoint tem um hoje — é rede para o próximo. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleMissingParameter(MissingServletRequestParameterException e) {
        logClientError("api.missing_parameter name={}", e.getParameterName());
        return ApiErrorResponse.withFields(400, "Parâmetro obrigatório ausente",
                Map.of(e.getParameterName(), "é obrigatório"));
    }

    /** Corpo ausente ou JSON malformado — o cliente errou o pedido, não o servidor. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleUnreadableBody(HttpMessageNotReadableException e) {
        logClientError("api.unreadable_body", null);
        return ApiErrorResponse.of(400, "Corpo da requisição ausente ou mal formado (esperado JSON).");
    }

    /** `Content-Type` que a API não aceita. 415 é o status próprio para isto. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public ApiErrorResponse handleUnsupportedMediaType(HttpMediaTypeNotSupportedException e) {
        logClientError("api.unsupported_media_type value={}", String.valueOf(e.getContentType()));
        return ApiErrorResponse.of(415, "Esta API aceita apenas application/json.");
    }

    /**
     * Método HTTP que o caminho não aceita (`GET /api/auth/login`,
     * `POST /api/notifications/unread-count`). Era **500**.
     *
     * O `Allow` é obrigatório num 405 pelo RFC 7231, e aqui não tem contraindicação
     * (diferente do `WWW-Authenticate` no 401, que faz alguns navegadores abrirem o
     * diálogo de basic auth): é a informação que resolve o problema de quem chamou.
     *
     * ⚠️ Só chega aqui caminho **sem ambiguidade**. `DELETE /api/lists/search` casa com
     * `DELETE /api/lists/{id}` e responde **400** ("id deve ser um número"), que é a
     * resposta certa — o Spring casou uma rota de verdade.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException e) {
        logClientError("api.method_not_allowed method={}", e.getMethod());
        HttpHeaders headers = new HttpHeaders();
        if (e.getSupportedHttpMethods() != null) {
            headers.setAllow(e.getSupportedHttpMethods());
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .headers(headers)
                .body(ApiErrorResponse.of(405, "Este endereço não aceita este método HTTP."));
    }

    /**
     * Caminho que não existe. Era **500** — e é o 500 mais fácil de provocar que a
     * API tinha: basta um crawler pedindo `/api/qualquer-coisa`. Exatamente o tipo de
     * ruído que faria o alerta de taxa de 5xx (T7) virar incidente por nada.
     *
     * ⚠️ Só chega aqui requisição **autenticada**: sem token, o Spring Security
     * responde 401 antes, e continua assim de propósito (não confirmar quais
     * caminhos existem para quem não está logado).
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleNoResource(NoResourceFoundException e) {
        logClientError("api.no_such_route path={}", e.getResourcePath());
        return ApiErrorResponse.of(404, "Este endereço não existe nesta API.");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiErrorResponse handleUnexpected(Exception e) {
        // Nunca vazar stacktrace/mensagem interna pro cliente.
        log.error("api.unexpected_error", e);
        return ApiErrorResponse.of(500, "Erro interno inesperado");
    }

    /**
     * Erro de cliente é **WARN sem stacktrace**: não é incidente, mas volume aqui
     * denuncia ou um cliente maluco ou um bug nosso mandando parâmetro errado — e o
     * stacktrace de uma conversão falhada não ajuda ninguém.
     */
    private void logClientError(String formato, String valor) {
        if (valor == null) {
            log.warn(formato);
        } else {
            log.warn(formato, valor);
        }
    }

    /** Tipos numéricos que aparecem em parâmetro de rota/query (inclui os primitivos). */
    private static final Set<Class<?>> NUMERICOS =
            Set.of(int.class, long.class, short.class, byte.class, double.class, float.class,
                    Integer.class, Long.class, Short.class, Byte.class, Double.class, Float.class);

    /**
     * O que dizer no `fieldErrors`. Para enum, listar os valores aceitos é a
     * informação mais útil possível (e eles já são públicos: o frontend os envia).
     * O **valor recebido** nunca é ecoado — não é segredo, mas repetir entrada do
     * cliente numa mensagem é hábito que um dia vira problema.
     */
    private String expectativa(Class<?> tipoEsperado) {
        if (tipoEsperado == null) {
            return "valor inválido";
        }
        if (tipoEsperado.isEnum()) {
            return "valores aceitos: " + Arrays.stream(tipoEsperado.getEnumConstants())
                    .map(String::valueOf)
                    .collect(Collectors.joining(", "));
        }
        if (NUMERICOS.contains(tipoEsperado)) {
            return "deve ser um número";
        }
        if (tipoEsperado == boolean.class || tipoEsperado == Boolean.class) {
            return "deve ser true ou false";
        }
        return "valor inválido";
    }
}
