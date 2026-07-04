package io.github.pratikpanchal22.authserver.oidc;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RegisteredClientRepositoryTest {

    @Autowired
    RegisteredClientRepository repository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void repositoryIsJdbcBacked() {
        assertThat(repository).isInstanceOf(JdbcRegisteredClientRepository.class);
    }

    @Test
    void storefrontClient_isSeededOnStartup() {
        RegisteredClient client = repository.findByClientId("storefront");
        assertThat(client).isNotNull();
        assertThat(client.getClientId()).isEqualTo("storefront");
        assertThat(client.getAuthorizationGrantTypes())
                .contains(AuthorizationGrantType.AUTHORIZATION_CODE, AuthorizationGrantType.REFRESH_TOKEN);
        assertThat(client.getScopes())
                .contains(OidcScopes.OPENID, OidcScopes.PROFILE, OidcScopes.EMAIL);
    }

    @Test
    void storefrontClient_secretVerifiesAgainstPlaintext() {
        RegisteredClient client = repository.findByClientId("storefront");
        assertThat(client).isNotNull();
        assertThat(passwordEncoder.matches("secret", client.getClientSecret())).isTrue();
    }
}
