package com.exivamoeres.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Sugestão automática de soul core para uma lista (ex.: criatura que nenhum
 * membro desbloqueou ainda, priorizada por dificuldade). Gerada por job.
 *
 * NOTA (sessão 2): geração e ciclo de vida ainda não implementados —
 * ver SuggestionService.
 */
@Entity
@Table(name = "soulcore_suggestions")
@Getter
@Setter
@NoArgsConstructor
public class SoulcoreSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "list_id", nullable = false)
    private HuntingList list;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creature_id", nullable = false)
    private Creature creature;

    /** Texto explicando por que a criatura foi sugerida. */
    @Column(nullable = false, length = 300)
    private String reason;

    @Column(nullable = false)
    private boolean dismissed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
