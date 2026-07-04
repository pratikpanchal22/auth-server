package io.github.pratikpanchal22.authserver.repository;

import io.github.pratikpanchal22.authserver.domain.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    List<AuditEvent> findByUserId(UUID userId);

    List<AuditEvent> findByEventType(String eventType);
}
