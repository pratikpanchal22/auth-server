package io.github.pratikpanchal22.authserver.oidc;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OidcDiscoveryTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void discoveryDocument_isPublicAndContainsRequiredOidcFields() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/.well-known/openid-configuration", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKeys(
                "issuer",
                "authorization_endpoint",
                "token_endpoint",
                "jwks_uri",
                "scopes_supported",
                "response_types_supported",
                "grant_types_supported",
                "subject_types_supported",
                "id_token_signing_alg_values_supported"
        );
    }

    @Test
    void discoveryDocument_issuerMatchesBaseUrl() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/.well-known/openid-configuration", Map.class);

        assertThat(response.getBody().get("issuer")).asString()
                .startsWith("http://localhost:");
    }

    @Test
    @SuppressWarnings("unchecked")
    void jwksEndpoint_returnsRsaPublicKey() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/oauth2/jwks", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> keys = (List<Map<String, Object>>) response.getBody().get("keys");
        assertThat(keys).isNotEmpty();
        Map<String, Object> key = keys.get(0);
        assertThat(key.get("kty")).isEqualTo("RSA");
        assertThat(key).containsKeys("n", "e", "kid");
        // Private key components must NOT be present in the public JWKS endpoint
        assertThat(key).doesNotContainKey("d");
    }
}
