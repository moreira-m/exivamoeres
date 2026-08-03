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
    VOCATION_NOT_IN_COMPOSITION;

    /**
     * O motivo é <b>definitivo</b>? Ou seja: nenhuma ação de quem pediu resolve.
     *
     * <p>É a mesma classificação que a ordem de precedência acima já usa, agora com nome —
     * porque duas regras dependem dela: qual motivo mostrar, e (item P28) <b>quando avisar
     * que o motivo trocou</b>. Sair de consertável para definitivo muda a ação da pessoa de
     * "jogue mais" para "use outro personagem", e isso vale um aviso; o contrário não.</p>
     *
     * <p>⚠️ O {@code switch} é exaustivo e <b>sem `default` de propósito</b>: motivo novo no
     * enum <b>não compila</b> até ser classificado aqui. Um `default` transformaria "esqueci
     * de decidir" em "consertável", que é o lado que não avisa — o silêncio seria a falha
     * padrão, e ninguém descobriria.</p>
     */
    public boolean isDefinitive() {
        return switch (this) {
            case WORLD_MISMATCH, VOCATION_NOT_IN_COMPOSITION -> true;
            case BELOW_MINIMUM_LEVEL -> false;
        };
    }

    /**
     * Trocar de {@code antes} para {@code agora} muda <b>o que quem pediu deve fazer</b>?
     * (item P28)
     *
     * <p>Só num sentido: de consertável para definitivo. A ação vira de "jogue mais" para
     * "use outro personagem", e sem aviso a pessoa segue subindo de level para um pedido
     * que já não pode ser aprovado. O contrário é boa notícia <b>parcial</b> (continua
     * inaprovável) e não vale tocar o sino de ninguém; entre dois definitivos a conclusão
     * é a mesma.</p>
     *
     * <p>⚠️ <b>"Mudou de motivo" não precisa ser testado aqui</b>: com
     * {@code agora.isDefinitive() && !antes.isDefinitive()}, motivos iguais são impossíveis
     * por construção ({@code x && !x}). A comparação explícita existia e foi removida — ela
     * passava a impressão de carregar peso, e uma mutação que a apagava não reprovava nada.</p>
     *
     * <p>Nulo em qualquer lado significa "cabia" ou "cabe", que são os outros dois ramos da
     * transição — não este.</p>
     */
    public static boolean actionChanged(JoinRequestIssue antes, JoinRequestIssue agora) {
        return antes != null && agora != null && agora.isDefinitive() && !antes.isDefinitive();
    }
}
