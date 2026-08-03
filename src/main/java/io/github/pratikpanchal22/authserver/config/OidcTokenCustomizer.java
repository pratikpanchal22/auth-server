package io.github.pratikpanchal22.authserver.config;

import io.github.pratikpanchal22.authserver.repository.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class OidcTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    private static final Set<String> FORWARDED_CLAIMS = Set.of("email", "name", "picture");

    private final UserRepository userRepository;

    public OidcTokenCustomizer(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void customize(JwtEncodingContext context) {
        if (!"id_token".equals(context.getTokenType().getValue())) return;

        var principal = context.getPrincipal().getPrincipal();

        // Forward upstream OIDC claims for SSO users
        if (principal instanceof OidcUser oidcUser) {
            var upstreamToken = oidcUser.getIdToken();
            for (String claim : FORWARDED_CLAIMS) {
                Object value = upstreamToken.getClaim(claim);
                if (value != null) {
                    context.getClaims().claim(claim, value);
                }
            }
        }

        // Roles — all users
        List<String> roles = context.getPrincipal().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        if (!roles.isEmpty()) {
            context.getClaims().claim("roles", roles);
        }

        // allowed_clients — loaded from DB by email.
        // For SSO users the email comes from OidcUser; for local users the principal
        // name is the email (UserDetailsServiceImpl uses email as the username).
        String email = principal instanceof OidcUser oidcUser
                ? oidcUser.getEmail()
                : context.getPrincipal().getName();
        if (email != null) {
            userRepository.findByEmail(email).ifPresent(user -> {
                Set<String> clients = user.getAllowedClients();
                if (!clients.isEmpty()) {
                    context.getClaims().claim("allowed_clients", List.copyOf(clients));
                }
            });
        }
    }
}
