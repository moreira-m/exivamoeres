package com.exivamoeres.repository;

import com.exivamoeres.domain.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByUserId(Long userId);

    /** Localização idempotente ao processar eventos do webhook do Stripe. */
    Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);
}
