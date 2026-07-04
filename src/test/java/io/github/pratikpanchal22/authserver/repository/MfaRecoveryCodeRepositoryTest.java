package io.github.pratikpanchal22.authserver.repository;

import io.github.pratikpanchal22.authserver.domain.AuthType;
import io.github.pratikpanchal22.authserver.domain.MfaRecoveryCode;
import io.github.pratikpanchal22.authserver.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
class MfaRecoveryCodeRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    MfaRecoveryCodeRepository recoveryCodeRepository;

    @Autowired
    UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        recoveryCodeRepository.deleteAll();
        userRepository.deleteAll();

        testUser = new User();
        testUser.setEmail("mfa-user@example.com");
        testUser.setAuthType(AuthType.LOCAL);
        testUser.setMfaEnabled(true);
        testUser = userRepository.save(testUser);
    }

    @Test
    void save_persistsRecoveryCode() {
        MfaRecoveryCode code = new MfaRecoveryCode();
        code.setUser(testUser);
        code.setCodeHash("hash-abc-123");

        MfaRecoveryCode saved = recoveryCodeRepository.save(code);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.isUsed()).isFalse();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void findByUserIdAndUsedFalse_returnsUnusedCodes() {
        MfaRecoveryCode unused1 = new MfaRecoveryCode();
        unused1.setUser(testUser);
        unused1.setCodeHash("hash-1");

        MfaRecoveryCode unused2 = new MfaRecoveryCode();
        unused2.setUser(testUser);
        unused2.setCodeHash("hash-2");

        MfaRecoveryCode used = new MfaRecoveryCode();
        used.setUser(testUser);
        used.setCodeHash("hash-3");
        used.setUsed(true);

        recoveryCodeRepository.save(unused1);
        recoveryCodeRepository.save(unused2);
        recoveryCodeRepository.save(used);

        List<MfaRecoveryCode> result = recoveryCodeRepository.findByUserIdAndUsedFalse(testUser.getId());

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(c -> !c.isUsed());
    }

    @Test
    void countByUserIdAndUsedFalse_returnsCorrectCount() {
        for (int i = 0; i < 8; i++) {
            MfaRecoveryCode code = new MfaRecoveryCode();
            code.setUser(testUser);
            code.setCodeHash("hash-" + i);
            recoveryCodeRepository.save(code);
        }

        assertThat(recoveryCodeRepository.countByUserIdAndUsedFalse(testUser.getId())).isEqualTo(8);
    }
}
