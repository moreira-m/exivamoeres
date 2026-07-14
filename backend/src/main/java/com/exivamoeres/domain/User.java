package com.exivamoeres.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Usuário do sistema. Três formas de existir:
 * - LOCAL: email + senha (hash BCrypt em passwordHash);
 * - GOOGLE/DISCORD: OAuth2 — email pode vir do provider, passwordHash é nulo;
 * - ANONYMOUS: sem email nem senha, identificado só pelo JWT emitido na criação.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nulo para contas anônimas. Único quando presente. */
    @Column(unique = true)
    private String email;

    /** Hash BCrypt. Nulo para contas OAuth e anônimas. */
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 20)
    private AuthProvider authProvider;

    /** Id do usuário no provider OAuth (sub do Google, id do Discord). */
    @Column(name = "provider_id")
    private String providerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isAnonymous() {
        return authProvider == AuthProvider.ANONYMOUS;
    }
}
