package com.exivamoeres.domain;

/**
 * Plano da conta no site. FREE tem limite de times ativos e prazo menor;
 * PREMIUM (assinatura Stripe) tem times ilimitados, prazo maior e destaque
 * automático nos anúncios.
 */
public enum Plan {
    FREE,
    PREMIUM
}
