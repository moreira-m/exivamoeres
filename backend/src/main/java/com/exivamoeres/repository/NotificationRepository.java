package com.exivamoeres.repository;

import com.exivamoeres.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findAllByRecipientIdOrderByCreatedAtDesc(Long recipientId, Pageable pageable);

    long countByRecipientIdAndReadFalse(Long recipientId);

    /** Marca todas as não-lidas do usuário como lidas de uma vez. */
    @Modifying
    @Query("update Notification n set n.read = true where n.recipient.id = :recipientId and n.read = false")
    int markAllReadByRecipientId(@Param("recipientId") Long recipientId);

    /**
     * Apaga em lote as notificações <b>lidas</b> criadas antes de {@code limite}
     * (job de retenção, S8).
     *
     * ⚠️ O `n.read = true` não é só o filtro do produto — é o que casa com o índice
     * <b>parcial</b> `ix_notifications_read_created` (V21). Tirá-lo, ou trocá-lo por
     * um "todas as antigas", apaga aviso que ninguém viu <b>e</b> derruba o plano
     * para seq scan.
     */
    @Modifying
    @Query("delete from Notification n where n.read = true and n.createdAt < :limite")
    int deleteReadBefore(@Param("limite") Instant limite);
}
