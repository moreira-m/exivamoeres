package com.exivamoeres.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Animus Mastery já desbloqueado por um personagem (soul core gasto no
 * Soulpit). Base para as sugestões automáticas: um core que o personagem já
 * desbloqueou não interessa mais a ele.
 *
 * NOTA (sessão 2): lógica de registro/importação ainda não implementada.
 */
@Entity
@Table(name = "character_soulcores")
@Getter
@Setter
@NoArgsConstructor
public class CharacterSoulcore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_id", nullable = false)
    private Character character;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creature_id", nullable = false)
    private Creature creature;

    @Column(name = "unlocked_at", nullable = false, updatable = false)
    private Instant unlockedAt;

    @PrePersist
    void onCreate() {
        if (unlockedAt == null) {
            unlockedAt = Instant.now();
        }
    }
}
