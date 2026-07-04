package io.github.pratikpanchal22.authserver.oidc;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthorizationFlowTest {

    private static final String REDIRECT_URI =
            "http://localhost:8080/login/oauth2/code/auth-server";

    @Autowired
    MockMvc mockMvc;

    @Test
    void authorizeEndpoint_unauthenticated_doesNotIssueCode() throws Exception {
        // Spring AS redirects a browser to /login (3xx) or returns an OAuth2 error (4xx).
        // MockMvc anonymous context triggers the 4xx path; a real browser session gets 3xx.
        // Either way, no authorization code must be issued.
        var result = mockMvc.perform(get("/oauth2/authorize")
                        .header(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml")
                        .param("response_type", "code")
                        .param("client_id", "storefront")
                        .param("redirect_uri", REDIRECT_URI)
                        .param("scope", "openid"))
                .andReturn();

        int status = result.getResponse().getStatus();
        assertThat(status).as("expected redirect-to-login or error, not a 2xx").isGreaterThanOrEqualTo(300);
        String location = result.getResponse().getHeader("Location");
        if (location != null) {
            assertThat(location).doesNotContain("code=");
        }
    }

    @Test
    void authorizeEndpoint_unknownClient_returnsError() throws Exception {
        mockMvc.perform(get("/oauth2/authorize")
                        .param("response_type", "code")
                        .param("client_id", "unknown-client")
                        .param("redirect_uri", REDIRECT_URI)
                        .param("scope", "openid"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void tokenEndpoint_withoutClientAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", "fake-code")
                        .param("redirect_uri", REDIRECT_URI))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenEndpoint_withWrongClientSecret_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .with(httpBasic("storefront", "wrong-secret"))
                        .param("grant_type", "authorization_code")
                        .param("code", "fake-code")
                        .param("redirect_uri", REDIRECT_URI))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenEndpoint_withValidClientButFakeCode_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .with(httpBasic("storefront", "secret"))
                        .param("grant_type", "authorization_code")
                        .param("code", "totally-invalid-code")
                        .param("redirect_uri", REDIRECT_URI))
                .andExpect(status().isBadRequest());
    }

    // Convenience helper — avoids static import collision with csrf()
    private static org.springframework.test.web.servlet.request.RequestPostProcessor httpBasic(
            String username, String password) {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .httpBasic(username, password);
    }
}
