package com.exivamoeres.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Notificação destinada a um usuário. Referencia opcionalmente o time
 * relacionado ao evento (nulo se o time já não fizer sentido). O texto é
 * montado no frontend a partir do type + dados do time, para respeitar o
 * idioma do usuário (i18n).
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    /** Time relacionado ao evento (pode ser nulo). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "list_id")
    private HuntingList list;

    /** Nome do time no momento do evento — preserva contexto mesmo se o time mudar. */
    @Column(name = "list_name", length = 100)
    private String listName;

    @Column(nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
