package com.exivamoeres.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Duration;
import java.time.Instant;

/**
 * Pedido de posse de um personagem. O usuário prova que controla o personagem
 * colocando o verificationCode no campo "Comment" do perfil em Tibia.com;
 * um job de polling confere via TibiaData API.
 *
 * lastCheckedAt só é atualizado quando a TibiaData respondeu de fato — falha
 * de rede não conta como checagem. A expiração (24h) é calculada sobre
 * createdAt, nunca sobre falhas de polling.
 */
@Entity
@Table(name = "character_claims")
@Getter
@Setter
@NoArgsConstructor
public class CharacterClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_id", nullable = false)
    private Character character;

    /** Quem está reivindicando a posse. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User claimant;

    @Column(name = "verification_code", nullable = false, length = 20)
    private String verificationCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClaimStatus status;

    /** Última resposta VÁLIDA da TibiaData para este claim. */
    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Momento em que saiu de PENDING (aprovado, rejeitado ou expirado). */
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        if (status == null) {
            status = ClaimStatus.PENDING;
        }
    }

    public boolean isExpired(Duration ttl) {
        return status == ClaimStatus.PENDING && createdAt.plus(ttl).isBefore(Instant.now());
    }
}
