package com.exivamoeres.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Soul core rastreado dentro de uma lista: quem lootou (OBTAINED) e se já foi
 * gasto no Soulpit (UNLOCKED).
 *
 * NOTA (sessão 2): lógica de marcar/transferir cores ainda não implementada —
 * ver SoulcoreService.
 */
@Entity
@Table(name = "list_soulcores")
@Getter
@Setter
@NoArgsConstructor
public class ListSoulcore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "list_id", nullable = false)
    private HuntingList list;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creature_id", nullable = false)
    private Creature creature;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SoulcoreStatus status;

    /** Personagem que lootou/possui o core (nulo se ainda não atribuído). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "obtained_by_character_id")
    private Character obtainedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
