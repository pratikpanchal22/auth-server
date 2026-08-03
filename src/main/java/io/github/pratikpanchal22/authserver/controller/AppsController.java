package io.github.pratikpanchal22.authserver.controller;

import io.github.pratikpanchal22.authserver.domain.ClientUiMetadata;
import io.github.pratikpanchal22.authserver.repository.ClientUiMetadataRepository;
import io.github.pratikpanchal22.authserver.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Set;

@Controller
public class AppsController {

    private final UserRepository userRepository;
    private final ClientUiMetadataRepository metadataRepository;

    public AppsController(UserRepository userRepository,
                          ClientUiMetadataRepository metadataRepository) {
        this.userRepository = userRepository;
        this.metadataRepository = metadataRepository;
    }

    @GetMapping("/apps")
    public String apps(@AuthenticationPrincipal Object principal, Model model) {
        String email = switch (principal) {
            case OidcUser u -> u.getEmail();
            case UserDetails u -> u.getUsername();
            default -> null;
        };

        List<ClientUiMetadata> apps = List.of();
        if (email != null) {
            Set<String> allowedClients = userRepository.findByEmail(email)
                    .map(u -> u.getAllowedClients())
                    .orElse(Set.of());
            if (!allowedClients.isEmpty()) {
                apps = metadataRepository.findByClientIdInAndVisibleTrue(
                        allowedClients, Sort.by("displayName"));
            }
        }

        model.addAttribute("apps", apps);
        return "apps";
    }
}
