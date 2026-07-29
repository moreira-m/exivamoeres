package com.exivamoeres.domain.exception;

/**
 * Credencial que não vale: senha errada, email inexistente, refresh token
 * desconhecido, expirado ou revogado. Vira <b>401</b>.
 *
 * Existe para separar "esta credencial não serve" de {@link BusinessRuleException}
 * ("entendi o pedido, mas ele viola uma regra" → 422). Por dentro as duas coisas se
 * pareciam — as duas são "não deu" —, mas para quem consome a API são reações
 * opostas: 401 é <i>mande o usuário autenticar de novo</i>, 422 é <i>corrija o
 * payload</i>. Com 422 em credencial, a reação certa era a menos provável.
 *
 * ⚠️ <b>A mensagem nunca diz qual parte falhou.</b> "Email ou senha incorretos" vale
 * para email inexistente e para senha errada (não permitir enumeração de contas), e
 * "sessão expirada" vale para token desconhecido, expirado e revogado. A distinção
 * fica no log estruturado, que é onde ajuda a depurar sem ajudar a atacar.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
