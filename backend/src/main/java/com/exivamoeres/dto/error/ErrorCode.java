package com.exivamoeres.dto.error;

/**
 * Código estável de uma recusa de regra de negócio — o que a tela usa para montar a frase
 * <b>no idioma do usuário</b> (item T2).
 *
 * <p><b>O problema que isto resolve.</b> O backend devolvia a frase pronta, sempre em
 * português: quem usa o site em inglês recebia *"O time está cheio (máximo de 5
 * jogadores)"*. Traduzir no backend seria pior (o idioma do usuário não é assunto de regra
 * de negócio, e o `Accept-Language` não chega em job nem em log).</p>
 *
 * <p><b>A migração é incremental de propósito.</b> Toda recusa continua mandando o
 * `message` em português como <b>reserva</b>: regra ainda não convertida funciona como
 * antes, e o frontend cai no `message` quando não conhece o código. Não existe "big bang" —
 * cada entrega converte o que toca.</p>
 *
 * <p><b>Quem adiciona um valor aqui adiciona a tradução nos dois idiomas</b> (chave
 * `errors.codes.<CODE>` em `pt.json` e `en.json`). Há teste de componente varrendo este
 * enum: código sem frase reprova, em vez de aparecer como chave crua na tela.</p>
 */
public enum ErrorCode {

    // ---- entrada em time: onde o usuário mais toma recusa ----
    /** O time chegou ao máximo de membros. Params: `max`. */
    TEAM_FULL,
    /** O personagem é de outro world. Params: `character`, `characterWorld`, `teamWorld`. */
    WORLD_MISMATCH,
    /** Free Account não participa de time. Params: `character`. */
    FREE_ACCOUNT,
    /** O personagem está abaixo do level mínimo. Params: `character`, `minimum`, `level`. */
    BELOW_MINIMUM_LEVEL,
    /** O personagem não existe no Tibia.com. Params: `character`. */
    CHARACTER_NOT_FOUND,
    /** A composição do time não prevê a vocação. Params: `vocation`. */
    VOCATION_WITHOUT_SLOT,
    /** Este personagem já é membro aprovado do time. */
    ALREADY_MEMBER,
    /** Já existe pedido pendente para este personagem. */
    PENDING_REQUEST_EXISTS,
    /** O time não está ACTIVE. Params: `status`. */
    TEAM_NOT_ACCEPTING,

    // ---- limites de plano ----
    /** Limite de times ativos do plano. Params: `limit`. */
    ACTIVE_TEAM_LIMIT
}
