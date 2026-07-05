package io.github.pratikpanchal22.authserver.controller;

import io.github.pratikpanchal22.authserver.repository.MfaRecoveryCodeRepository;
import io.github.pratikpanchal22.authserver.repository.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/account")
public class AccountController {

    private final UserRepository userRepository;
    private final MfaRecoveryCodeRepository recoveryCodeRepository;

    public AccountController(UserRepository userRepository,
                             MfaRecoveryCodeRepository recoveryCodeRepository) {
        this.userRepository = userRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
    }

    @GetMapping
    public String account(@AuthenticationPrincipal Object principal, Model model) {
        String email = resolveEmail(principal);
        model.addAttribute("email", email);
        userRepository.findByEmail(email).ifPresent(user -> {
            model.addAttribute("authType", user.getAuthType().name());
            model.addAttribute("mfaEnabled", user.isMfaEnabled());
            model.addAttribute("mfaRequired", user.isMfaRequired());
            model.addAttribute("recoveryCodesRemaining",
                    user.isMfaEnabled() ? recoveryCodeRepository.countByUserIdAndUsedFalse(user.getId()) : 0);
        });
        return "account";
    }

    @PostMapping("/mfa/disable")
    @Transactional
    public String disableMfa(@AuthenticationPrincipal Object principal, RedirectAttributes ra) {
        String email = resolveEmail(principal);
        userRepository.findByEmail(email).ifPresent(user -> {
            if (user.isMfaRequired()) return; // cannot self-disable when admin requires it
            user.setMfaEnabled(false);
            user.setTotpSecretRef(null);
            user.setTotpFailedAttempts(0);
            recoveryCodeRepository.deleteByUser_Id(user.getId());
            userRepository.save(user);
        });
        ra.addFlashAttribute("success", "Two-factor authentication has been disabled.");
        return "redirect:/account";
    }

    private static String resolveEmail(Object principal) {
        return switch (principal) {
            case OidcUser u -> u.getEmail();
            case UserDetails u -> u.getUsername();
            default -> "unknown";
        };
    }
}
