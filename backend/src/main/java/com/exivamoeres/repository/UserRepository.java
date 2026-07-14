package com.exivamoeres.repository;

import com.exivamoeres.domain.AuthProvider;
import com.exivamoeres.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByAuthProviderAndProviderId(AuthProvider provider, String providerId);

    boolean existsByEmailIgnoreCase(String email);
}
