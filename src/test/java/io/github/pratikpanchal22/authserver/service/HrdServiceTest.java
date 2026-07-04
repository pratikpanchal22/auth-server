package io.github.pratikpanchal22.authserver.service;

import io.github.pratikpanchal22.authserver.domain.IdentityProvider;
import io.github.pratikpanchal22.authserver.repository.IdentityProviderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HrdServiceTest {

    @Mock
    private IdentityProviderRepository idpRepository;

    @InjectMocks
    private HrdService hrdService;

    @Test
    void lookup_knownFederatedDomain_returnsFederated() {
        when(idpRepository.findByEnabledTrue()).thenReturn(List.of(idp("google", "gmail.com,googlemail.com")));

        var result = hrdService.lookup("alice@gmail.com");

        assertThat(result.method()).isEqualTo("FEDERATED");
        assertThat(result.registrationId()).isEqualTo("google");
    }

    @Test
    void lookup_unknownDomain_returnsLocal() {
        when(idpRepository.findByEnabledTrue()).thenReturn(List.of(idp("google", "gmail.com")));

        var result = hrdService.lookup("alice@example.com");

        assertThat(result.method()).isEqualTo("LOCAL");
        assertThat(result.registrationId()).isNull();
    }

    @Test
    void lookup_noIdpsConfigured_returnsLocal() {
        when(idpRepository.findByEnabledTrue()).thenReturn(List.of());

        var result = hrdService.lookup("alice@gmail.com");

        assertThat(result.method()).isEqualTo("LOCAL");
    }

    @Test
    void lookup_domainMatchIsCaseInsensitive() {
        when(idpRepository.findByEnabledTrue()).thenReturn(List.of(idp("google", "Gmail.Com")));

        var result = hrdService.lookup("alice@GMAIL.COM");

        assertThat(result.method()).isEqualTo("FEDERATED");
    }

    @Test
    void lookup_idpWithNullEmailDomains_skipped() {
        when(idpRepository.findByEnabledTrue()).thenReturn(List.of(idp("google", null)));

        var result = hrdService.lookup("alice@gmail.com");

        assertThat(result.method()).isEqualTo("LOCAL");
    }

    @Test
    void extractDomain_validEmail_returnsDomain() {
        assertThat(HrdService.extractDomain("alice@example.com")).isEqualTo("example.com");
    }

    @Test
    void extractDomain_noAtSign_returnsNull() {
        assertThat(HrdService.extractDomain("notanemail")).isNull();
    }

    @Test
    void extractDomain_nullInput_returnsNull() {
        assertThat(HrdService.extractDomain(null)).isNull();
    }

    private IdentityProvider idp(String name, String emailDomains) {
        var idp = new IdentityProvider();
        idp.setName(name);
        idp.setIssuerUrl("https://accounts.google.com");
        idp.setClientId("client-id");
        idp.setClientSecretRef("secret");
        idp.setEmailDomains(emailDomains);
        idp.setEnabled(true);
        return idp;
    }
}
