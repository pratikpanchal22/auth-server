package io.github.pratikpanchal22.authserver.repository;

import io.github.pratikpanchal22.authserver.domain.IdentityProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IdentityProviderRepository extends JpaRepository<IdentityProvider, UUID> {

    Optional<IdentityProvider> findByName(String name);

    List<IdentityProvider> findByEnabledTrue();
}
