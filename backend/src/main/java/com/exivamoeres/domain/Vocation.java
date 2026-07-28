package com.exivamoeres.domain;

/**
 * Vocação <b>base</b> de um personagem de Tibia, para efeito de composição de time.
 *
 * <p><b>Base e não promovida de propósito.</b> A TibiaData devolve a vocação já
 * promovida ("Elder Druid", "Royal Paladin"), mas um Druid e um Elder Druid ocupam
 * o mesmo <i>papel</i> no grupo. Guardar o papel é o que permite dizer "faltam 1 EK
 * e 1 ED" sem multiplicar cada vaga por duas variações — e o level, que é o que
 * diferencia na prática um promovido de um não promovido, já é exigido por
 * `minimum_level`.</p>
 *
 * <p>{@link #NONE} é o "sem vocação" (personagem novo, ou string que não
 * reconhecemos). Ele <b>não</b> preenche vaga com vocação exigida — só vaga livre.</p>
 */
public enum Vocation {
    KNIGHT,
    PALADIN,
    SORCERER,
    DRUID,
    MONK,
    NONE;

    /**
     * Converte o texto da TibiaData (`"Elite Knight"`, `"druid"`, `"None"`, nulo…)
     * na vocação base.
     *
     * <p>Tolerante de propósito — é a mesma lição do
     * {@code CommentCodeMatcher}: dado que vem de fora nunca é comparado por
     * igualdade exata. Texto desconhecido cai em {@link #NONE} em vez de estourar:
     * uma vocação nova no jogo não pode derrubar o site (só deixa de preencher vaga
     * restrita até alguém acrescentar o valor aqui).</p>
     */
    public static Vocation fromTibiaData(String vocation) {
        if (vocation == null || vocation.isBlank()) {
            return NONE;
        }
        String normalizado = vocation.trim().toLowerCase();
        if (normalizado.contains("knight")) {
            return KNIGHT;
        }
        if (normalizado.contains("paladin")) {
            return PALADIN;
        }
        if (normalizado.contains("sorcerer")) {
            return SORCERER;
        }
        if (normalizado.contains("druid")) {
            return DRUID;
        }
        if (normalizado.contains("monk")) {
            return MONK;
        }
        return NONE;
    }

    /** Esta vocação pode ocupar uma vaga que exige {@code exigida} (nula = livre)? */
    public boolean fits(Vocation exigida) {
        if (exigida == null) {
            return true; // vaga livre aceita qualquer um, inclusive NONE
        }
        return this == exigida;
    }
}
