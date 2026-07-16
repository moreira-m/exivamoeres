package com.exivamoeres.repository;

import com.exivamoeres.domain.CharacterClaim;
import com.exivamoeres.domain.ClaimStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CharacterClaimRepository extends JpaRepository<CharacterClaim, Long> {

    /** Varredura do job de polling — atende pelo índice parcial de PENDING. */
    List<CharacterClaim> findAllByStatus(ClaimStatus status);

    /**
     * Claim com o personagem já carregado — a verificação lê o nome fora de
     * transação (entre a consulta ao banco e a chamada HTTP), então o lazy
     * proxy não serviria.
     */
    @EntityGraph(attributePaths = "character")
    Optional<CharacterClaim> findWithCharacterById(Long id);

    List<CharacterClaim> findAllByClaimantIdOrderByCreatedAtDesc(Long claimantId);

    /**
     * Também carrega o personagem: CharacterClaimServiceImpl monta a resposta
     * (ClaimResponse.from) fora de transação em alguns caminhos (ex.:
     * verifyNow), então o proxy lazy quebraria com LazyInitializationException.
     */
    @EntityGraph(attributePaths = "character")
    Optional<CharacterClaim> findByIdAndClaimantId(Long id, Long claimantId);

    /** Evita dois claims PENDING simultâneos do mesmo usuário para o mesmo personagem. */
    boolean existsByCharacterIdAndClaimantIdAndStatus(Long characterId, Long claimantId, ClaimStatus status);
}
