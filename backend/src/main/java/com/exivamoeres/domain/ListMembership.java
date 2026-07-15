package com.exivamoeres.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Participação (ou pedido de participação) de um usuário num time através de
 * um personagem específico.
 *
 * active = false quando o membro sai ou quando o personagem troca de dono
 * (a aprovação de um CharacterClaim desativa as memberships do dono anterior)
 * — histórico nunca é deletado, só desativado.
 *
 * status controla o fluxo de aprovação: PENDING (aguardando o dono do time),
 * APPROVED (membro de fato) ou REJECTED (pedido recusado). Uma linha é
 * reaproveitada (nunca duplicada) para o mesmo par (list, character) —
 * pedir de novo depois de sair/ser recusado reativa o registro existente.
 */
@Entity
@Table(name = "list_memberships")
@Getter
@Setter
@NoArgsConstructor
public class ListMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "list_id", nullable = false)
    private HuntingList list;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_id", nullable = false)
    private Character character;

    @Column(nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MembershipStatus status;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @PrePersist
    void onCreate() {
        joinedAt = Instant.now();
        if (status == null) {
            status = MembershipStatus.PENDING;
        }
    }
}
