package com.exivamoeres.dto.list;

import com.exivamoeres.domain.TeamStatus;

import java.util.Arrays;
import java.util.Collection;

/**
 * Qual metade de "meus times" o cliente quer (item P12).
 *
 * <p>A tela de `/account/teams` tem abas — <b>ativos</b> e <b>concluídos/arquivados</b> — e
 * antes recebia as duas juntas, numa lista sem página nem teto. Medido numa conta com 12
 * times: <b>9 dos 12 itens eram histórico</b>, que a tela nem mostra ao abrir. O recorte é do
 * servidor agora, e cada metade vem paginada.</p>
 *
 * <p>⚠️ <b>Não existe "todos"</b>, e é de propósito: era exatamente o valor sem teto. Toda
 * resposta deste endpoint é uma página de uma das duas metades.</p>
 */
public enum MyTeamsScope {

    /** Os que estão valendo agora — a aba que a tela abre. */
    ACTIVE,

    /**
     * O histórico: concluído, arquivado, encerrado.
     *
     * ⚠️ Definido como "tudo que <b>não</b> é ACTIVE", e não por uma lista de status. Um
     * {@link TeamStatus} novo cai aqui sozinho — visível em algum lugar, que é o padrão
     * seguro. Com lista fechada ele ficaria <b>invisível nas duas abas</b>, e ninguém
     * descobriria: a soma das duas nunca é conferida contra o total.
     */
    HISTORY;

    public Collection<TeamStatus> statuses() {
        return this == ACTIVE
                ? java.util.List.of(TeamStatus.ACTIVE)
                : Arrays.stream(TeamStatus.values()).filter(s -> s != TeamStatus.ACTIVE).toList();
    }
}
