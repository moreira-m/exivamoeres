package com.exivamoeres.service;

import com.exivamoeres.dto.claim.ClaimResponse;

import java.util.List;

/** Casos de uso do fluxo de claim expostos pela API REST. */
public interface CharacterClaimService {

    /**
     * Inicia um claim: valida que o personagem existe no Tibia.com (via
     * TibiaData), cria o registro local do personagem se necessário e gera o
     * verification_code que o usuário colocará no Comment do perfil.
     */
    ClaimResponse startClaim(Long userId, String characterName);

    ClaimResponse getClaim(Long claimId, Long userId);

    List<ClaimResponse> listClaims(Long userId);

    /**
     * Verificação sob demanda (POST /api/claims/{id}/verify-now) — mesma
     * regra do job de polling, sem esperar o próximo ciclo de 15 minutos.
     */
    ClaimResponse verifyNow(Long claimId, Long userId);
}
