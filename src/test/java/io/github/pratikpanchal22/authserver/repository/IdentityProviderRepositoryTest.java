package io.github.pratikpanchal22.authserver.repository;

import io.github.pratikpanchal22.authserver.domain.IdentityProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
class IdentityProviderRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    IdentityProviderRepository idpRepository;

    @BeforeEach
    void setUp() {
        idpRepository.deleteAll();
    }

    @Test
    void save_persistsIdp() {
        IdentityProvider idp = new IdentityProvider();
        idp.setName("google");
        idp.setIssuerUrl("https://accounts.google.com");
        idp.setClientId("client-id");
        idp.setClientSecretRef("arn:aws:secretsmanager:us-east-1:123:secret:google-secret");

        IdentityProvider saved = idpRepository.save(idp);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getScopes()).isEqualTo("openid,profile,email");
    }

    @Test
    void findByName_returnsIdp() {
        IdentityProvider idp = new IdentityProvider();
        idp.setName("okta");
        idp.setIssuerUrl("https://dev-123.okta.com");
        idp.setClientId("client-id");
        idp.setClientSecretRef("arn:aws:secretsmanager:us-east-1:123:secret:okta-secret");
        idpRepository.save(idp);

        Optional<IdentityProvider> found = idpRepository.findByName("okta");

        assertThat(found).isPresent();
        assertThat(found.get().getIssuerUrl()).isEqualTo("https://dev-123.okta.com");
    }

    @Test
    void findByName_nonExistent_returnsEmpty() {
        assertThat(idpRepository.findByName("azure")).isEmpty();
    }

    @Test
    void findByEnabledTrue_returnsOnlyEnabledIdps() {
        IdentityProvider enabled = new IdentityProvider();
        enabled.setName("google");
        enabled.setIssuerUrl("https://accounts.google.com");
        enabled.setClientId("cid");
        enabled.setClientSecretRef("arn:aws:secretsmanager:us-east-1:123:secret:google");
        enabled.setEnabled(true);

        IdentityProvider disabled = new IdentityProvider();
        disabled.setName("okta");
        disabled.setIssuerUrl("https://dev.okta.com");
        disabled.setClientId("cid2");
        disabled.setClientSecretRef("arn:aws:secretsmanager:us-east-1:123:secret:okta");
        disabled.setEnabled(false);

        idpRepository.save(enabled);
        idpRepository.save(disabled);

        List<IdentityProvider> result = idpRepository.findByEnabledTrue();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("google");
    }
}
