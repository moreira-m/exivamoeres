package com.exivamoeres.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Time organizado para caçar UMA criatura-alvo (targetCreature) e conseguir
 * o Soul Core dela. Vinculado a um world porque personagens só caçam juntos
 * no mesmo mundo. O shareCode permite entrar no time por link.
 */
@Entity
@Table(name = "hunting_lists")
@Getter
@Setter
@NoArgsConstructor
public class HuntingList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 40)
    private String world;

    @Column(name = "share_code", nullable = false, unique = true, length = 20)
    private String shareCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_creature_id", nullable = false)
    private Creature targetCreature;

    /** Escolhida pelo criador: aprovação manual ou entrada automática. */
    @Enumerated(EnumType.STRING)
    @Column(name = "join_policy", nullable = false, length = 20)
    private JoinPolicy joinPolicy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TeamStatus status = TeamStatus.ACTIVE;

    /** Level mínimo exigido para entrar (opcional; nulo = sem restrição). */
    @Column(name = "minimum_level")
    private Integer minimumLevel;

    /**
     * Preço INFORMATIVO por vaga em gold do jogo, definido pelo criador.
     * Não é uma transação processada pelo sistema. Opcional (nulo = não informado).
     */
    @Column(name = "price_per_slot")
    private Long pricePerSlot;

    /** Momento em que o time expira (some da busca e vira só leitura). */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

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

    public boolean allowsWrites() {
        return status.allowsWrites();
    }
}
