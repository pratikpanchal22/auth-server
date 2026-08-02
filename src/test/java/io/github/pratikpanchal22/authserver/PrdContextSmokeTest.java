package io.github.pratikpanchal22.authserver;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Loads the production profile with required env vars supplied explicitly.
 * Fails if a new ${VAR} with no default is added to application-prd.properties
 * without also being provided here — forcing the author to document the
 * new requirement and add it to the start script / SSM.
 */
@SpringBootTest
@ActiveProfiles("prd")
@TestPropertySource(properties = {
        // Required by application-prd.properties (no Spring defaults)
        "AUTH_SERVER_BASE_URL=http://test.example.com",
        "DB_URL=jdbc:h2:mem:prdtestdb;DB_CLOSE_DELAY=-1",
        "DB_USER=sa",
        "DB_PASSWORD=",
        // H2 overrides so context starts without a real PostgreSQL instance
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class PrdContextSmokeTest {

    @Value("${auth.server.base-url}")
    private String authServerBaseUrl;

    @Test
    void contextLoads() {
    }

    @Test
    void authServerBaseUrlIsResolved() {
        assertThat(authServerBaseUrl).isNotBlank();
    }
}
