package com.exivamoeres.repository;

import com.exivamoeres.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteByUserId(Long userId);

    /**
     * Apaga em lote os tokens vencidos antes de {@code limite} (job de retenção, S8).
     *
     * ⚠️ `@Query` em vez do derivado `deleteAllByExpiresAtBefore`: o derivado <b>carrega
     * cada entidade</b> e apaga uma por uma — numa tabela que existe justamente por ser
     * grande, isso é a diferença entre um DELETE e milhares. Índice:
     * `ix_refresh_tokens_expires_at` (V21).
     */
    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :limite")
    int deleteExpiredBefore(@Param("limite") Instant limite);
}
