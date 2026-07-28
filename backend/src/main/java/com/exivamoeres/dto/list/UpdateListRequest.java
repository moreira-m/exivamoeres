package com.exivamoeres.dto.list;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Campos editáveis de um time já criado (só o dono, só em time ATIVO).
 *
 * <p><b>Semântica: o payload é o conjunto COMPLETO dos campos editáveis.</b> O
 * formulário manda todos, e campo nulo/em branco significa <b>limpar</b> — não
 * "não mexer". É o que permite apagar uma descrição que não serve mais sem
 * inventar um valor sentinela.</p>
 *
 * <p><b>O que NÃO está aqui é decisão, não esquecimento:</b> `world`,
 * `targetCreatureId` e `joinPolicy` não são editáveis. Mudar o world invalidaria
 * todos os membros de uma vez, e mudar a criatura faria o time deixar de ser
 * aquilo que as pessoas entraram para caçar. Para isso, o certo é um time novo.
 * Campo desconhecido no JSON é ignorado (Jackson), então mandá-los não faz nada.</p>
 */
public record UpdateListRequest(
        /** Título; em branco volta a assumir o nome da criatura-alvo. */
        @Size(max = 100)
        String name,

        /**
         * Level mínimo exigido dos <b>próximos</b> membros (nulo = sem restrição).
         * Não reavalia quem já foi aprovado — ver {@code HuntingListServiceImpl}.
         */
        @Min(1)
        Integer minimumLevel,

        /** Preço informativo por vaga em gold (nulo = não informado). */
        @PositiveOrZero
        Long pricePerSlot,

        @Size(max = 500)
        String description,

        @Size(max = 120)
        String huntSchedule,

        /** ⚠️ Dado pessoal: só volta para o dono e membros aprovados. */
        @Size(max = 120)
        String contact
) {
}
