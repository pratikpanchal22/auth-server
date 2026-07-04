package io.github.pratikpanchal22.authserver.service;

import io.github.pratikpanchal22.authserver.domain.AuthType;
import io.github.pratikpanchal22.authserver.domain.User;
import io.github.pratikpanchal22.authserver.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JitOidcUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private JitOidcUserService service;

    @Test
    void provision_newUser_savesWithFederatedAuthType() {
        service.provision("alice@example.com");

        var captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getAuthType()).isEqualTo(AuthType.FEDERATED);
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getPasswordHash()).isNull();
        assertThat(saved.getRoles()).containsExactly("USER");
    }

    @Test
    void provision_setsActiveTrue() {
        service.provision("bob@example.com");

        var captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isTrue();
    }

    @Test
    void provision_setsRoleUser() {
        service.provision("carol@example.com");

        var captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRoles()).containsExactly("USER");
    }
}
