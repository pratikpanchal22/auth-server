package io.github.pratikpanchal22.authserver.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/access-denied")
    public String accessDenied() {
        return "access-denied";
    }

    @GetMapping("/")
    public String home(@AuthenticationPrincipal Object principal, Model model) {
        String email = switch (principal) {
            case OidcUser u -> u.getEmail();
            case UserDetails u -> u.getUsername();
            default -> "unknown";
        };
        model.addAttribute("email", email);
        return "index";
    }
}
