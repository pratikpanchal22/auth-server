package io.github.pratikpanchal22.authserver.repository;

import io.github.pratikpanchal22.authserver.domain.MfaRecoveryCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MfaRecoveryCodeRepository extends JpaRepository<MfaRecoveryCode, UUID> {

    List<MfaRecoveryCode> findByUserIdAndUsedFalse(UUID userId);

    long countByUserIdAndUsedFalse(UUID userId);

    void deleteByUser_Id(UUID userId);
}

