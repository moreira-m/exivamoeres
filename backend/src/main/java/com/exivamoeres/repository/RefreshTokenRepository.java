package com.exivamoeres.repository;

import com.exivamoeres.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * Revoga <b>todos</b> os tokens vivos do usuário — a reação a um token
     * reapresentado depois de rotacionado (S7, detecção de reuso).
     *
     * ⚠️ {@code REQUIRES_NEW} de propósito, e é o detalhe que faz a defesa existir:
     * quem chama recusa a requisição <b>lançando exceção</b>, e a exceção desfaz a
     * transação da requisição inteira. Na transação de quem chama, esta revogação
     * seria escrita e desfeita no mesmo instante — o log diria que reagiu e o banco
     * continuaria com a sessão do ladrão de pé.
     */
    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("update RefreshToken t set t.revoked = true where t.user.id = :userId and t.revoked = false")
    int revokeAllForUser(@Param("userId") Long userId);
}
