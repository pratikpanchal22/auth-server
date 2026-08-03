package io.github.pratikpanchal22.authserver.repository;

import io.github.pratikpanchal22.authserver.domain.ClientUiMetadata;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ClientUiMetadataRepository extends JpaRepository<ClientUiMetadata, String> {

    Optional<ClientUiMetadata> findByClientId(String clientId);

    List<ClientUiMetadata> findByClientIdInAndVisibleTrue(Collection<String> clientIds, Sort sort);
}
