package com.exivamoeres.service.impl;

import com.exivamoeres.client.TibiaCharacterSnapshot;
import com.exivamoeres.client.TibiaDataClient;
import com.exivamoeres.config.ClaimProperties;
import com.exivamoeres.domain.CharacterClaim;
import com.exivamoeres.domain.ClaimStatus;
import com.exivamoeres.domain.exception.ResourceNotFoundException;
import com.exivamoeres.repository.CharacterClaimRepository;
import com.exivamoeres.service.ClaimVerificationService;
import com.exivamoeres.service.CommentCodeMatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * Decide o destino de cada claim PENDING consultando a TibiaData.
 *
 * Regras críticas:
 * - expiração é calculada sobre created_at (24h), nunca sobre falha de rede;
 * - falha de comunicação => UNREACHABLE: nada muda no claim, será rechecado
 *   no próximo ciclo (o retry/backoff acontece dentro do TibiaDataClient);
 * - a decisão de matching fica inteira no CommentCodeMatcher.
 */
@Service
@Slf4j
public class ClaimVerificationServiceImpl implements ClaimVerificationService {

    /** Teto de espera por claim — protege o ciclo do job de travar. */
    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(20);

    private final CharacterClaimRepository claimRepository;
    private final TibiaDataClient tibiaDataClient;
    private final CommentCodeMatcher commentCodeMatcher;
    private final ClaimTransitionService transitionService;
    private final ClaimProperties claimProperties;

    public ClaimVerificationServiceImpl(CharacterClaimRepository claimRepository,
                                        TibiaDataClient tibiaDataClient,
                                        CommentCodeMatcher commentCodeMatcher,
                                        ClaimTransitionService transitionService,
                                        ClaimProperties claimProperties) {
        this.claimRepository = claimRepository;
        this.tibiaDataClient = tibiaDataClient;
        this.commentCodeMatcher = commentCodeMatcher;
        this.transitionService = transitionService;
        this.claimProperties = claimProperties;
    }

    @Override
    public void verifyPendingClaims() {
        List<CharacterClaim> pending = claimRepository.findAllByStatus(ClaimStatus.PENDING);
        log.info("claim.poll.start pendingCount={}", pending.size());
        // Um claim problemático não pode derrubar o ciclo inteiro:
        // cada verificação é isolada e falhas são apenas logadas.
        for (CharacterClaim claim : pending) {
            try {
                verifyClaim(claim.getId());
            } catch (Exception e) {
                log.error("claim.poll.claim_error claimId={} error={}", claim.getId(), e.toString());
            }
        }
        log.info("claim.poll.end pendingCount={}", pending.size());
    }

    @Override
    public VerificationOutcome verifyClaim(Long claimId) {
        CharacterClaim claim = claimRepository.findWithCharacterById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Claim não encontrado: " + claimId));
        if (claim.getStatus() != ClaimStatus.PENDING) {
            throw new IllegalStateException("Claim " + claimId + " não está pendente");
        }

        if (claim.isExpired(claimProperties.expiration())) {
            transitionService.expire(claimId);
            return VerificationOutcome.EXPIRED;
        }

        TibiaCharacterSnapshot snapshot;
        try {
            // block() deliberado: o WebClient é não bloqueante (com retry e
            // circuit breaker reativos), mas a transição de estado é JPA e
            // transação JPA é presa à thread — o bloqueio aqui é a fronteira
            // entre os dois mundos.
            snapshot = tibiaDataClient.fetchCharacter(claim.getCharacter().getName())
                    .block(FETCH_TIMEOUT);
        } catch (Exception e) {
            log.warn("claim.verify.unreachable claimId={} character='{}' error={}",
                    claimId, claim.getCharacter().getName(), e.toString());
            return VerificationOutcome.UNREACHABLE;
        }

        return applySnapshot(claim, snapshot);
    }

    private VerificationOutcome applySnapshot(CharacterClaim claim, TibiaCharacterSnapshot snapshot) {
        if (snapshot == null) {
            return VerificationOutcome.UNREACHABLE;
        }
        // Personagem sumiu do Tibia.com (deletado/renomeado): resposta válida,
        // conta como checagem; o claim segue pendente até expirar.
        String comment = snapshot.found() ? snapshot.comment() : null;

        if (commentCodeMatcher.matches(comment, claim.getVerificationCode())) {
            transitionService.approve(claim.getId());
            return VerificationOutcome.APPROVED;
        }
        transitionService.markCheckedWithoutMatch(claim.getId());
        return VerificationOutcome.CODE_NOT_FOUND;
    }
}
