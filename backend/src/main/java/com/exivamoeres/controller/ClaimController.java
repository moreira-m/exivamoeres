package com.exivamoeres.controller;

import com.exivamoeres.dto.claim.ClaimResponse;
import com.exivamoeres.dto.claim.CreateClaimRequest;
import com.exivamoeres.security.AuthenticatedUser;
import com.exivamoeres.service.CharacterClaimService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/claims")
public class ClaimController {

    private final CharacterClaimService claimService;

    public ClaimController(CharacterClaimService claimService) {
        this.claimService = claimService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClaimResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                @Valid @RequestBody CreateClaimRequest request) {
        return claimService.startClaim(user.id(), request.characterName());
    }

    @GetMapping
    public List<ClaimResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return claimService.listClaims(user.id());
    }

    @GetMapping("/{id}")
    public ClaimResponse get(@AuthenticationPrincipal AuthenticatedUser user,
                             @PathVariable Long id) {
        return claimService.getClaim(id, user.id());
    }

    /** Verificação sob demanda, sem esperar o ciclo de 15 minutos do job. */
    @PostMapping("/{id}/verify-now")
    public ClaimResponse verifyNow(@AuthenticationPrincipal AuthenticatedUser user,
                                   @PathVariable Long id) {
        return claimService.verifyNow(id, user.id());
    }
}
