package com.exivamoeres.service.impl;

import com.exivamoeres.domain.Character;
import com.exivamoeres.domain.CharacterClaim;
import com.exivamoeres.domain.ClaimStatus;
import com.exivamoeres.domain.ListMembership;
import com.exivamoeres.domain.exception.ResourceNotFoundException;
import com.exivamoeres.repository.CharacterClaimRepository;
import com.exivamoeres.repository.ListMembershipRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Transições de estado do claim, cada uma numa transação própria.
 *
 * Classe separada (e não métodos privados do ClaimVerificationServiceImpl)
 * por dois motivos: responsabilidade única, e porque @Transactional só
 * funciona através do proxy do Spring — auto-invocação não abre transação.
 */
@Service
@Slf4j
public class ClaimTransitionService {

    private final CharacterClaimRepository claimRepository;
    private final ListMembershipRepository membershipRepository;

    public ClaimTransitionService(CharacterClaimRepository claimRepository,
                                  ListMembershipRepository membershipRepository) {
        this.claimRepository = claimRepository;
        this.membershipRepository = membershipRepository;
    }

    /**
     * Aprovação atômica: aprovar o claim, transferir a posse do personagem e
     * desativar as memberships do dono anterior acontecem juntos ou nada
     * acontece — um personagem não pode ficar com dono novo e memberships do
     * dono antigo ativas.
     */
    @Transactional
    public void approve(Long claimId) {
        CharacterClaim claim = loadPending(claimId);
        Character character = claim.getCharacter();

        deactivateMembershipsOfPreviousOwner(character);
        character.setOwner(claim.getClaimant());

        Instant now = Instant.now();
        claim.setStatus(ClaimStatus.APPROVED);
        claim.setLastCheckedAt(now);
        claim.setResolvedAt(now);

        log.info("claim.approved claimId={} characterId={} newOwnerId={}",
                claimId, character.getId(), claim.getClaimant().getId());
    }

    /** Rejeita claim PENDING que passou das 24h sem verificação. */
    @Transactional
    public void expire(Long claimId) {
        CharacterClaim claim = loadPending(claimId);
        claim.setStatus(ClaimStatus.REJECTED);
        claim.setResolvedAt(Instant.now());
        log.info("claim.expired claimId={} characterId={}", claimId, claim.getCharacter().getId());
    }

    /**
     * Registra que a TibiaData respondeu mas o código não estava no comment.
     * Só chega aqui com resposta válida da API — falha de rede nunca
     * atualiza last_checked_at.
     */
    @Transactional
    public void markCheckedWithoutMatch(Long claimId) {
        CharacterClaim claim = loadPending(claimId);
        claim.setLastCheckedAt(Instant.now());
        log.info("claim.checked.no_match claimId={} characterId={}",
                claimId, claim.getCharacter().getId());
    }

    private CharacterClaim loadPending(Long claimId) {
        CharacterClaim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Claim não encontrado: " + claimId));
        if (claim.getStatus() != ClaimStatus.PENDING) {
            throw new IllegalStateException(
                    "Transição inválida: claim " + claimId + " está " + claim.getStatus());
        }
        return claim;
    }

    private void deactivateMembershipsOfPreviousOwner(Character character) {
        List<ListMembership> memberships =
                membershipRepository.findAllByCharacterIdAndActiveTrue(character.getId());
        memberships.forEach(membership -> membership.setActive(false));
        if (!memberships.isEmpty()) {
            log.info("claim.memberships.deactivated characterId={} count={}",
                    character.getId(), memberships.size());
        }
    }
}
