package com.exivamoeres.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Assinatura Stripe de um usuário (1:1). Fonte de verdade/auditoria do estado
 * da assinatura; o campo User.plan é o cache derivado usado nas autorizações.
 *
 * O status é o valor cru do Stripe (active, past_due, canceled, ...) — não
 * mapeamos para um enum próprio para não quebrar se o Stripe evoluir.
 */
@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "stripe_subscription_id", nullable = false, unique = true)
    private String stripeSubscriptionId;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

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
