package io.github.pratikpanchal22.authserver.config;

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

    @Override
    public void customize(JwtEncodingContext context) {
        if (!"id_token".equals(context.getTokenType().getValue())) return;

        // Forward upstream OIDC claims for SSO users
        var principal = context.getPrincipal().getPrincipal();
        if (principal instanceof OidcUser oidcUser) {
            var upstreamToken = oidcUser.getIdToken();
            for (String claim : FORWARDED_CLAIMS) {
                Object value = upstreamToken.getClaim(claim);
                if (value != null) {
                    context.getClaims().claim(claim, value);
                }
            }
        }

        // Include roles for all users (local and SSO)
        List<String> roles = context.getPrincipal().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        if (!roles.isEmpty()) {
            context.getClaims().claim("roles", roles);
        }
    }
}
