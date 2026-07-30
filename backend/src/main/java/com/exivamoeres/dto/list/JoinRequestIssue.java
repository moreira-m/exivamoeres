package com.exivamoeres.dto.list;

/**
 * Motivo pelo qual um pedido pendente <b>provavelmente</b> não será aprovado.
 *
 * <p>É <b>código</b>, não frase: quem monta o texto é o frontend, no idioma do
 * usuário. Isso segue a direção do
 * <a href="../../../../../../../NEXT_STEPS.md">T2</a> (códigos de erro em vez de
 * português cravado no backend) — não faz sentido nascer devendo.</p>
 *
 * <p><b>"Provavelmente" é literal.</b> Os três são detectáveis com dado
 * <b>local</b> (level, world e vocação sincronizados do personagem × o requisito
 * atual do time), sem gastar consulta à TibiaData. Perda de Premium Account, por
 * exemplo, <b>não</b> aparece aqui — então ausência de aviso não é garantia de
 * aprovação. A decisão real acontece quando o dono aprova.</p>
 *
 * <p><b>Ordem de precedência</b> (só um motivo é devolvido): primeiro o que a pessoa
 * <b>não pode consertar</b>. World e vocação são definitivos — nenhuma aprovação vai
 * acontecer, e a ação é usar outro personagem; level é temporário, e a ação é jogar.
 * Mostrar o motivo consertável quando existe um definitivo faz a pessoa gastar tempo
 * no lugar errado.</p>
 */
public enum JoinRequestIssue {
    /** O time exige level maior do que o do personagem do pedido. */
    BELOW_MINIMUM_LEVEL,
    /** O personagem não é mais do world do time (world transfer). */
    WORLD_MISMATCH,
    /**
     * A composição do time <b>não tem vaga</b> para a vocação do personagem — o dono
     * reconfigurou as vagas depois do pedido.
     *
     * <p>Note que <b>vaga ocupada não é problema</b>: pedido para vaga que já tem
     * alguém é legítimo (a pessoa pode sair, e é assim que time cheio com aprovação
     * manual funciona). O que este código diz é mais forte: a composição nem
     * <b>prevê</b> esta vocação, então a aprovação nunca vai poder acontecer.</p>
     */
    VOCATION_NOT_IN_COMPOSITION
}
