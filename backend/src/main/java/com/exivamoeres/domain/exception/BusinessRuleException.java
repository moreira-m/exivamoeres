package com.exivamoeres.domain.exception;

import com.exivamoeres.dto.error.ErrorCode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Violação de regra de negócio (vira 422).
 *
 * <p>Pode carregar um {@link ErrorCode} e os valores da frase, para a tela traduzir no
 * idioma do usuário (item T2). O construtor de uma linha continua existindo: regra não
 * convertida funciona como antes, mandando só a frase em português.</p>
 *
 * <p>O uso é fluente no ponto do `throw`, para o código ficar ao lado da frase que ele
 * substitui:</p>
 *
 * <pre>{@code
 * throw new BusinessRuleException(ErrorCode.TEAM_FULL,
 *         "O time está cheio (máximo de " + maxMembers + " jogadores)")
 *         .with("max", maxMembers);
 * }</pre>
 */
public class BusinessRuleException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, String> params = new LinkedHashMap<>();

    /** Recusa ainda **não** convertida: só a frase em português. */
    public BusinessRuleException(String message) {
        this(null, message);
    }

    public BusinessRuleException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    /** Acrescenta um valor para a frase da tela. Devolve `this` para uso no `throw`. */
    public BusinessRuleException with(String chave, Object valor) {
        params.put(chave, String.valueOf(valor));
        return this;
    }

    public ErrorCode getCode() {
        return code;
    }

    public Map<String, String> getParams() {
        return Map.copyOf(params);
    }
}
