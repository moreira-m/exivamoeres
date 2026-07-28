package com.exivamoeres.dto.list;

/**
 * Motivo pelo qual um pedido pendente <b>provavelmente</b> não será aprovado.
 *
 * <p>É <b>código</b>, não frase: quem monta o texto é o frontend, no idioma do
 * usuário. Isso segue a direção do
 * <a href="../../../../../../../NEXT_STEPS.md">T2</a> (códigos de erro em vez de
 * português cravado no backend) — não faz sentido nascer devendo.</p>
 *
 * <p><b>"Provavelmente" é literal.</b> Estes dois são detectáveis com dado
 * <b>local</b> (o level e o world sincronizados do personagem × o requisito atual
 * do time), sem gastar consulta à TibiaData. Perda de Premium Account, por
 * exemplo, <b>não</b> aparece aqui — então ausência de aviso não é garantia de
 * aprovação. A decisão real acontece quando o dono aprova.</p>
 */
public enum JoinRequestIssue {
    /** O time exige level maior do que o do personagem do pedido. */
    BELOW_MINIMUM_LEVEL,
    /** O personagem não é mais do world do time (world transfer). */
    WORLD_MISMATCH
}
