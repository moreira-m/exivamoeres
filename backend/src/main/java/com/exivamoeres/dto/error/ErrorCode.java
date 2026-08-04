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
 * `errors.codes.<CODE>` em `pt.json` e `en.json`) <b>e o valor na união
 * `ErrorCode` do `types/api.ts`</b>. Não é disciplina: o
 * `frontend/scripts/error-codes-check.mjs` lê este arquivo e <b>reprova o build</b> se
 * faltar qualquer um dos três. Antes dele, a lista do frontend era um espelho copiado à
 * mão — código novo passava sem frase e ninguém sabia até alguém em inglês tomar a
 * recusa.</p>

 * <p>⚠️ <b>Não é todo `BusinessRuleException` que merece código aqui.</b> Se a recusa não
 * é regra de negócio, o problema é o status, não a tradução: rate limit é <b>429</b>
 * ({@code TooManyRequestsException}) e falha interna é <b>5xx</b>. Dar código a essas
 * cimentaria o status errado — ver T18.</p>
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
    ACTIVE_TEAM_LIMIT,

    // ---------------------------------------------------------------------------
    // Segunda fase (T18): as recusas que sobraram. Agrupadas por **quem lê** — é o
    // que decide o tom da frase, e o que separa "corrija" de "não é possível".
    // ---------------------------------------------------------------------------

    // ---- o dono mexendo no próprio time ----
    /** O time não aceita mais escrita (não está ACTIVE). Params: `status`. */
    TEAM_LOCKED,
    /** Renovar só vale para time arquivado. Params: `status`. */
    RENEW_REQUIRES_ARCHIVED,
    /** O time já foi encerrado. */
    TEAM_ALREADY_CLOSED,
    /** O dono não pode sair do próprio time. */
    OWNER_CANNOT_LEAVE,
    /** O dono não pode expulsar a si mesmo. */
    OWNER_CANNOT_KICK_SELF,
    /**
     * O level mínimo pedido é maior que o do personagem do próprio dono no time.
     * Params: `minimum`, `ownerLevel`.
     */
    OWNER_BELOW_OWN_MINIMUM,

    // ---- composição ----
    /** A composição nova deixaria de fora um membro que já está no time. Params: `vocation`, `character`. */
    COMPOSITION_EXCLUDES_MEMBER,
    /** Não há vaga **livre** compatível com a vocação. Params: `vocation`. */
    NO_FREE_SLOT_FOR_VOCATION,
    /** A composição tem mais vagas que o tamanho do time. Params: `max`. */
    COMPOSITION_TOO_LARGE,

    // ---- pedido de entrada, do lado do dono ----
    /**
     * A aprovação foi bloqueada pela elegibilidade do candidato.
     *
     * <p>⚠️ <b>Params: `reason`</b> — o {@link ErrorCode} do motivo real
     * ({@code BELOW_MINIMUM_LEVEL}, {@code FREE_ACCOUNT}…), <b>mais os params dele</b>. É
     * um código que carrega outro código: a tela monta "não foi possível aprovar porque
     * ⟨motivo⟩" traduzindo os dois. Antes disso, a frase do dono era a concatenação de
     * duas em português — a mensagem mais longa do produto — e o código do motivo era
     * <b>descartado</b> no reembrulho.</p>
     */
    APPROVAL_BLOCKED,
    /** O pedido já foi decidido (aprovado, recusado ou cancelado). */
    REQUEST_NOT_PENDING,

    // ---- participação e personagem ----
    /** Quem chamou não participa deste time. */
    NOT_A_MEMBER,
    /** O personagem não é do usuário. */
    CHARACTER_NOT_YOURS,
    /** O personagem não é membro ativo deste time. */
    CHARACTER_NOT_ACTIVE_MEMBER,
    /** O personagem ainda não teve o claim aprovado. */
    CHARACTER_NOT_VERIFIED,
    /** O usuário já é o dono deste personagem. */
    ALREADY_CHARACTER_OWNER,
    /** Já existe claim pendente deste usuário para este personagem. */
    CLAIM_ALREADY_PENDING,

    // ---- conta ----
    /** Já existe conta com este email. */
    EMAIL_ALREADY_REGISTERED
}
