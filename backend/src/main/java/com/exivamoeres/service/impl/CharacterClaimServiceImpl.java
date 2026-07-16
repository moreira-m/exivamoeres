package com.exivamoeres.service.impl;

import com.exivamoeres.client.TibiaCharacterSnapshot;
import com.exivamoeres.client.TibiaDataClient;
import com.exivamoeres.config.ClaimProperties;
import com.exivamoeres.domain.Character;
import com.exivamoeres.domain.CharacterClaim;
import com.exivamoeres.domain.ClaimStatus;
import com.exivamoeres.domain.User;
import com.exivamoeres.domain.exception.BusinessRuleException;
import com.exivamoeres.domain.exception.ExternalServiceException;
import com.exivamoeres.domain.exception.ResourceNotFoundException;
import com.exivamoeres.dto.claim.ClaimResponse;
import com.exivamoeres.repository.CharacterClaimRepository;
import com.exivamoeres.repository.UserRepository;
import com.exivamoeres.service.CharacterClaimService;
import com.exivamoeres.service.CharacterSyncService;
import com.exivamoeres.service.VerificationCodeGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Service
@Slf4j
public class CharacterClaimServiceImpl implements CharacterClaimService {

    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(20);

    private final CharacterClaimRepository claimRepository;
    private final CharacterSyncService characterSyncService;
    private final UserRepository userRepository;
    private final TibiaDataClient tibiaDataClient;
    private final VerificationCodeGenerator codeGenerator;
    private final ClaimProperties claimProperties;

    public CharacterClaimServiceImpl(CharacterClaimRepository claimRepository,
                                     CharacterSyncService characterSyncService,
                                     UserRepository userRepository,
                                     TibiaDataClient tibiaDataClient,
                                     VerificationCodeGenerator codeGenerator,
                                     ClaimProperties claimProperties) {
        this.claimRepository = claimRepository;
        this.characterSyncService = characterSyncService;
        this.userRepository = userRepository;
        this.tibiaDataClient = tibiaDataClient;
        this.codeGenerator = codeGenerator;
        this.claimProperties = claimProperties;
    }

    @Override
    @Transactional
    public ClaimResponse startClaim(Long userId, String characterName) {
        User claimant = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        // Consulta a TibiaData ANTES de criar qualquer coisa: claim de
        // personagem inexistente nunca aprovaria e só poluiria o job.
        TibiaCharacterSnapshot snapshot = fetchFromTibiaData(characterName);
        if (!snapshot.found()) {
            throw new BusinessRuleException(
                    "Personagem '" + characterName + "' não encontrado no Tibia.com");
        }

        Character character = characterSyncService.findOrCreateFromSnapshot(snapshot);
        validateClaimAllowed(character, claimant);

        CharacterClaim claim = new CharacterClaim();
        claim.setCharacter(character);
        claim.setClaimant(claimant);
        claim.setStatus(ClaimStatus.PENDING);
        claim.setVerificationCode(codeGenerator.generate());
        claimRepository.save(claim);

        log.info("claim.created claimId={} characterId={} userId={}",
                claim.getId(), character.getId(), userId);
        return ClaimResponse.from(claim, claimProperties.expiration());
    }

    @Override
    @Transactional(readOnly = true)
    public ClaimResponse getClaim(Long claimId, Long userId) {
        return ClaimResponse.from(loadOwnClaim(claimId, userId), claimProperties.expiration());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClaimResponse> listClaims(Long userId) {
        return claimRepository.findAllByClaimantIdOrderByCreatedAtDesc(userId).stream()
                .map(claim -> ClaimResponse.from(claim, claimProperties.expiration()))
                .toList();
    }

    private CharacterClaim loadOwnClaim(Long claimId, Long userId) {
        return claimRepository.findByIdAndClaimantId(claimId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Claim não encontrado"));
    }

    private TibiaCharacterSnapshot fetchFromTibiaData(String characterName) {
        TibiaCharacterSnapshot snapshot = tibiaDataClient.fetchCharacter(characterName)
                .block(FETCH_TIMEOUT);
        if (snapshot == null) {
            throw new ExternalServiceException("TibiaData não respondeu");
        }
        return snapshot;
    }

    private void validateClaimAllowed(Character character, User claimant) {
        if (character.getOwner() != null && character.getOwner().getId().equals(claimant.getId())) {
            throw new BusinessRuleException("Você já é o dono deste personagem");
        }
        boolean alreadyPending = claimRepository.existsByCharacterIdAndClaimantIdAndStatus(
                character.getId(), claimant.getId(), ClaimStatus.PENDING);
        if (alreadyPending) {
            throw new BusinessRuleException(
                    "Você já tem um claim pendente para este personagem");
        }
    }
}
