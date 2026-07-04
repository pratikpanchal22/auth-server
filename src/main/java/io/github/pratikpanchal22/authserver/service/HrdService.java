package io.github.pratikpanchal22.authserver.service;

import io.github.pratikpanchal22.authserver.repository.IdentityProviderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

@Service
public class HrdService {

    private final IdentityProviderRepository idpRepository;

    public HrdService(IdentityProviderRepository idpRepository) {
        this.idpRepository = idpRepository;
    }

    public record HrdResult(String method, String registrationId) {
        public static HrdResult local() { return new HrdResult("LOCAL", null); }
        public static HrdResult federated(String id) { return new HrdResult("FEDERATED", id); }
    }

    @Transactional(readOnly = true)
    public HrdResult lookup(String email) {
        String domain = extractDomain(email);
        if (domain == null) return HrdResult.local();

        return idpRepository.findByEnabledTrue().stream()
                .filter(idp -> idp.getEmailDomains() != null)
                .filter(idp -> Arrays.stream(idp.getEmailDomains().split(","))
                        .map(String::trim)
                        .anyMatch(domain::equalsIgnoreCase))
                .findFirst()
                .map(idp -> HrdResult.federated(idp.getName()))
                .orElseGet(HrdResult::local);
    }

    static String extractDomain(String email) {
        if (email == null) return null;
        int at = email.lastIndexOf('@');
        return at >= 0 && at < email.length() - 1
                ? email.substring(at + 1).toLowerCase()
                : null;
    }
}
