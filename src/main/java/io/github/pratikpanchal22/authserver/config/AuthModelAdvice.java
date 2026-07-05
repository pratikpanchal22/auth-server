package io.github.pratikpanchal22.authserver.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class AuthModelAdvice {

    @Value("${storefront.base-url:http://localhost:8080}")
    private String storefrontUrl;

    @ModelAttribute("storefrontUrl")
    public String storefrontUrl() {
        return storefrontUrl;
    }

    @ModelAttribute("displayName")
    public String displayName(@AuthenticationPrincipal Object principal) {
        if (principal instanceof OidcUser oidcUser) {
            String email = oidcUser.getEmail();
            return email != null ? email : oidcUser.getName();
        }
        if (principal instanceof UserDetails ud) {
            return ud.getUsername();
        }
        return "";
    }

    @ModelAttribute("avatarUrl")
    public String avatarUrl(@AuthenticationPrincipal Object principal) {
        if (principal instanceof OidcUser oidcUser) {
            return oidcUser.getPicture();
        }
        return null;
    }
}
