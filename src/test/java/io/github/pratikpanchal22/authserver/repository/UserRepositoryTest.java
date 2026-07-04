package io.github.pratikpanchal22.authserver.repository;

import io.github.pratikpanchal22.authserver.domain.AuthType;
import io.github.pratikpanchal22.authserver.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
class UserRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void save_persistsLocalUser() {
        User user = new User();
        user.setEmail("alice@example.com");
        user.setPasswordHash("$2a$10$hash");
        user.setAuthType(AuthType.LOCAL);
        user.getRoles().add("USER");

        User saved = userRepository.save(user);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void findByEmail_returnsUser() {
        User user = new User();
        user.setEmail("bob@example.com");
        user.setAuthType(AuthType.LOCAL);
        userRepository.save(user);

        Optional<User> found = userRepository.findByEmail("bob@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("bob@example.com");
    }

    @Test
    void findByEmail_nonExistent_returnsEmpty() {
        Optional<User> found = userRepository.findByEmail("ghost@example.com");
        assertThat(found).isEmpty();
    }

    @Test
    void existsByEmail_returnsTrue_whenUserExists() {
        User user = new User();
        user.setEmail("carol@example.com");
        user.setAuthType(AuthType.FEDERATED);
        userRepository.save(user);

        assertThat(userRepository.existsByEmail("carol@example.com")).isTrue();
    }

    @Test
    void existsByEmail_returnsFalse_whenUserAbsent() {
        assertThat(userRepository.existsByEmail("nobody@example.com")).isFalse();
    }

    @Test
    void delete_removesUser() {
        User user = new User();
        user.setEmail("dave@example.com");
        user.setAuthType(AuthType.LOCAL);
        User saved = userRepository.save(user);

        userRepository.deleteById(saved.getId());

        assertThat(userRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void roles_arePersistedAndLoaded() {
        User user = new User();
        user.setEmail("eve@example.com");
        user.setAuthType(AuthType.LOCAL);
        user.setRoles(Set.of("ADMIN", "USER"));
        userRepository.save(user);

        User loaded = userRepository.findByEmail("eve@example.com").orElseThrow();
        assertThat(loaded.getRoles()).containsExactlyInAnyOrder("ADMIN", "USER");
    }
}
