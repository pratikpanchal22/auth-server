package io.github.pratikpanchal22.authserver.repository;

import io.github.pratikpanchal22.authserver.domain.AuditEvent;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
class AuditEventRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    AuditEventRepository auditEventRepository;

    @Autowired
    UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        auditEventRepository.deleteAll();
        userRepository.deleteAll();

        testUser = new User();
        testUser.setEmail("audit-user@example.com");
        testUser.setAuthType(AuthType.LOCAL);
        testUser = userRepository.save(testUser);
    }

    @Test
    void save_persistsAuditEvent() {
        AuditEvent event = new AuditEvent();
        event.setUserId(testUser.getId());
        event.setEventType("LOGIN_SUCCESS");
        event.setIpAddress("192.168.1.1");
        event.setUserAgent("Mozilla/5.0");
        event.setMetadata("{\"browser\":\"Chrome\"}");

        AuditEvent saved = auditEventRepository.save(event);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void save_anonymousEvent_hasNullUserId() {
        AuditEvent event = new AuditEvent();
        event.setEventType("LOGIN_UNKNOWN_EMAIL");
        event.setIpAddress("10.0.0.1");

        AuditEvent saved = auditEventRepository.save(event);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUserId()).isNull();
    }

    @Test
    void findByUserId_returnsEventsForUser() {
        User otherUser = new User();
        otherUser.setEmail("other@example.com");
        otherUser.setAuthType(AuthType.LOCAL);
        otherUser = userRepository.save(otherUser);

        AuditEvent e1 = new AuditEvent();
        e1.setUserId(testUser.getId());
        e1.setEventType("LOGIN_SUCCESS");

        AuditEvent e2 = new AuditEvent();
        e2.setUserId(testUser.getId());
        e2.setEventType("LOGOUT");

        AuditEvent other = new AuditEvent();
        other.setUserId(otherUser.getId());
        other.setEventType("LOGIN_SUCCESS");

        auditEventRepository.save(e1);
        auditEventRepository.save(e2);
        auditEventRepository.save(other);

        List<AuditEvent> result = auditEventRepository.findByUserId(testUser.getId());

        assertThat(result).hasSize(2);
        assertThat(result).extracting(AuditEvent::getEventType)
                .containsExactlyInAnyOrder("LOGIN_SUCCESS", "LOGOUT");
    }

    @Test
    void findByEventType_returnsMatchingEvents() {
        AuditEvent e1 = new AuditEvent();
        e1.setEventType("LOGIN_FAILED");
        e1.setUserId(testUser.getId());

        AuditEvent e2 = new AuditEvent();
        e2.setEventType("LOGIN_FAILED");

        AuditEvent e3 = new AuditEvent();
        e3.setEventType("LOGIN_SUCCESS");
        e3.setUserId(testUser.getId());

        auditEventRepository.save(e1);
        auditEventRepository.save(e2);
        auditEventRepository.save(e3);

        List<AuditEvent> failures = auditEventRepository.findByEventType("LOGIN_FAILED");
        assertThat(failures).hasSize(2);
    }
}
