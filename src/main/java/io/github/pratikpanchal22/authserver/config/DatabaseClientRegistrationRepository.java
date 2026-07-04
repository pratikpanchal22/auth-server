package io.github.pratikpanchal22.authserver.config;

import io.github.pratikpanchal22.authserver.repository.IdentityProviderRepository;
import io.github.pratikpanchal22.authserver.service.SecretsService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DatabaseClientRegistrationRepository implements ClientRegistrationRepository {

    private final IdentityProviderRepository idpRepository;
    private final SecretsService secretsService;

    // In-process cache — avoids repeated OIDC discovery HTTP calls per request.
    // Invalidated on restart; acceptable since IDP configs change rarely.
    private final Map<String, ClientRegistration> cache = new ConcurrentHashMap<>();

    public DatabaseClientRegistrationRepository(IdentityProviderRepository idpRepository,
                                                SecretsService secretsService) {
        this.idpRepository = idpRepository;
        this.secretsService = secretsService;
    }

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        return cache.computeIfAbsent(registrationId, this::loadFromDb);
    }

    private ClientRegistration loadFromDb(String registrationId) {
        var idp = idpRepository.findByName(registrationId)
                .filter(p -> p.isEnabled())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No enabled IDP found with name: " + registrationId));

        String secret = secretsService.resolve(idp.getClientSecretRef());
        String[] scopes = idp.getScopes().split(",");

        return ClientRegistrations.fromIssuerLocation(idp.getIssuerUrl())
                .registrationId(registrationId)
                .clientId(idp.getClientId())
                .clientSecret(secret)
                .scope(scopes)
                .build();
    }
}
