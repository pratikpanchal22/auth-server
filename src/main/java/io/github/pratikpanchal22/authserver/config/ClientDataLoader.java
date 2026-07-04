package io.github.pratikpanchal22.authserver.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
@Profile("!prod")
class ClientDataLoader implements ApplicationRunner {

    private final RegisteredClientRepository repository;
    private final PasswordEncoder passwordEncoder;

    ClientDataLoader(RegisteredClientRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedStorefront();
        seedCalibreWeb();
        seedDrawio();
    }

    private void seedStorefront() {
        if (repository.findByClientId("storefront") != null) return;
        repository.save(RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("storefront")
                .clientSecret(passwordEncoder.encode("secret"))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://localhost:8080/login/oauth2/code/auth-server")
                .redirectUri("http://127.0.0.1:8080/login/oauth2/code/auth-server")
                .postLogoutRedirectUri("http://localhost:8080/")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope(OidcScopes.EMAIL)
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(defaultTokenSettings())
                .build());
    }

    private void seedCalibreWeb() {
        if (repository.findByClientId("calibre-web") != null) return;
        repository.save(RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("calibre-web")
                .clientSecret(passwordEncoder.encode("calibre-web-secret"))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://localhost:8083/login")
                .redirectUri("http://127.0.0.1:8083/login")
                .redirectUri("https://books.nthnode.com/login")
                .postLogoutRedirectUri("http://localhost:8083/")
                .postLogoutRedirectUri("https://books.nthnode.com/")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope(OidcScopes.EMAIL)
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(defaultTokenSettings())
                .build());
    }

    private void seedDrawio() {
        if (repository.findByClientId("drawio") != null) return;
        repository.save(RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("drawio")
                .clientSecret(passwordEncoder.encode("drawio-secret"))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://localhost:8888/")
                .redirectUri("http://127.0.0.1:8888/")
                .redirectUri("https://draw.nthnode.com/")
                .postLogoutRedirectUri("http://localhost:8888/")
                .postLogoutRedirectUri("https://draw.nthnode.com/")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope(OidcScopes.EMAIL)
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(defaultTokenSettings())
                .build());
    }

    private static TokenSettings defaultTokenSettings() {
        return TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofMinutes(15))
                .refreshTokenTimeToLive(Duration.ofHours(8))
                .reuseRefreshTokens(false)
                .build();
    }
}
