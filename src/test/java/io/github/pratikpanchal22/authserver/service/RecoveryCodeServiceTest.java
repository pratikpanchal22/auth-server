package io.github.pratikpanchal22.authserver.service;

import io.github.pratikpanchal22.authserver.domain.MfaRecoveryCode;
import io.github.pratikpanchal22.authserver.domain.User;
import io.github.pratikpanchal22.authserver.repository.MfaRecoveryCodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecoveryCodeServiceTest {

    @Mock
    private MfaRecoveryCodeRepository repository;

    // Use a real encoder so hash/verify round-trips work
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private RecoveryCodeService service() {
        return new RecoveryCodeService(repository, passwordEncoder);
    }

    @Test
    void generateAndStore_produces10Codes() {
        User user = new User();
        service().generateAndStore(user);
        verify(repository, times(10)).save(any(MfaRecoveryCode.class));
    }

    @Test
    void generateAndStore_codesAreUnique() {
        User user = new User();
        List<String> codes = service().generateAndStore(user);
        assertThat(codes).hasSize(10).doesNotHaveDuplicates();
    }

    @Test
    void generateAndStore_codeLength8() {
        User user = new User();
        service().generateAndStore(user).forEach(c -> assertThat(c).hasSize(8));
    }

    @Test
    void consumeRecoveryCode_validCode_returnsTrue() {
        RecoveryCodeService svc = service();
        User user = new User();
        user.setId(UUID.randomUUID());

        String plain = "ABCDEFGH";
        MfaRecoveryCode rc = new MfaRecoveryCode();
        rc.setCodeHash(passwordEncoder.encode(plain));

        when(repository.findByUserIdAndUsedFalse(user.getId())).thenReturn(List.of(rc));

        assertThat(svc.consumeRecoveryCode(user, plain)).isTrue();

        var captor = ArgumentCaptor.forClass(MfaRecoveryCode.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isUsed()).isTrue();
        assertThat(captor.getValue().getUsedAt()).isNotNull();
    }

    @Test
    void consumeRecoveryCode_invalidCode_returnsFalse() {
        RecoveryCodeService svc = service();
        User user = new User();
        user.setId(UUID.randomUUID());

        MfaRecoveryCode rc = new MfaRecoveryCode();
        rc.setCodeHash(passwordEncoder.encode("ABCDEFGH"));

        when(repository.findByUserIdAndUsedFalse(user.getId())).thenReturn(List.of(rc));

        assertThat(svc.consumeRecoveryCode(user, "ZZZZZZZZ")).isFalse();
        verify(repository, never()).save(any());
    }
}
