package com.exivamoeres.dto.claim;

import com.exivamoeres.domain.CharacterClaim;
import com.exivamoeres.domain.ClaimStatus;

import java.time.Duration;
import java.time.Instant;

public record ClaimResponse(
        Long id,
        String characterName,
        String world,
        String verificationCode,
        ClaimStatus status,
        Instant lastCheckedAt,
        Instant createdAt,
        Instant expiresAt
) {
    public static ClaimResponse from(CharacterClaim claim, Duration ttl) {
        return new ClaimResponse(
                claim.getId(),
                claim.getCharacter().getName(),
                claim.getCharacter().getWorld(),
                claim.getVerificationCode(),
                claim.getStatus(),
                claim.getLastCheckedAt(),
                claim.getCreatedAt(),
                claim.getCreatedAt().plus(ttl));
    }
}
