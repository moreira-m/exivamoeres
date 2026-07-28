package com.exivamoeres.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Uma vaga da composição do time: posição + vocação exigida.
 *
 * <p>{@code vocation} nula = <b>vaga livre</b> (aceita qualquer vocação). Um time
 * ou tem a composição inteira configurada (uma vaga por posição, até
 * {@code app.team.max-members}) ou não tem vaga nenhuma — e aí não há restrição de
 * vocação, que é o comportamento de todos os times criados antes da V17.</p>
 *
 * <p><b>Uma vocação por vaga, não um conjunto.</b> "2 EK, 2 ED, 1 qualquer" cobre o
 * que se anuncia no jogo, e o formulário vira um seletor por linha. Se um dia
 * precisar de "healer (ED ou MS)", o caminho é uma tabela de junção — não é preciso
 * antecipar isso agora.</p>
 */
@Entity
@Table(name = "team_slots")
@Getter
@Setter
@NoArgsConstructor
public class TeamSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "list_id", nullable = false)
    private HuntingList list;

    @Column(nullable = false)
    private int position;

    /** Nula = vaga livre. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Vocation vocation;

    public boolean accepts(Vocation candidata) {
        return candidata != null && candidata.fits(vocation);
    }
}
