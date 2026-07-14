package com.exivamoeres.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Lista colaborativa de soul cores de um grupo de caça. Vinculada a um world
 * porque personagens só caçam juntos no mesmo mundo. O shareCode permite
 * entrar na lista por link, sem convite individual.
 *
 * NOTA (sessão 2): a lógica de negócio (criar/entrar/gerenciar) ainda não foi
 * implementada — ver HuntingListService.
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
