package com.exivamoeres.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Personagem do Tibia. O nome é único no jogo inteiro (case-insensitive —
 * garantido por índice em lower(name) na migration). A posse (owner) só é
 * atribuída via CharacterClaim aprovado; um personagem pode existir sem dono
 * (ex.: citado numa lista antes de alguém verificá-lo).
 */
@Entity
@Table(name = "characters")
@Getter
@Setter
@NoArgsConstructor
public class Character {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false, length = 40)
    private String world;

    /** Sincronizada da TibiaData sempre que o personagem é consultado (claim, elegibilidade). */
    @Column(length = 30)
    private String vocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
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
