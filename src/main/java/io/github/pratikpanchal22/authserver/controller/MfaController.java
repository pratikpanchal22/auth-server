package io.github.pratikpanchal22.authserver.controller;

import io.github.pratikpanchal22.authserver.config.MfaAuthenticationSuccessHandler;
import io.github.pratikpanchal22.authserver.repository.UserRepository;
import io.github.pratikpanchal22.authserver.service.AuditService;
import io.github.pratikpanchal22.authserver.service.LoginTrackingService;
import io.github.pratikpanchal22.authserver.service.RecoveryCodeService;
import io.github.pratikpanchal22.authserver.service.TotpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/mfa")
public class MfaController {

    private static final String PENDING_TOTP_SECRET = "PENDING_TOTP_SECRET";

    private final TotpService totpService;
    private final RecoveryCodeService recoveryCodeService;
    private final UserRepository userRepository;
    private final LoginTrackingService loginTrackingService;
    private final AuditService auditService;
    private final HttpSessionSecurityContextRepository contextRepository =
            new HttpSessionSecurityContextRepository();

    public MfaController(TotpService totpService,
                         RecoveryCodeService recoveryCodeService,
                         UserRepository userRepository,
                         LoginTrackingService loginTrackingService,
                         AuditService auditService) {
        this.totpService = totpService;
        this.recoveryCodeService = recoveryCodeService;
        this.userRepository = userRepository;
        this.loginTrackingService = loginTrackingService;
        this.auditService = auditService;
    }

    // ── Challenge ────────────────────────────────────────────────────────────

    @GetMapping("/challenge")
    public String challengeForm() {
        return "mfa/challenge";
    }

    @PostMapping("/challenge")
    @Transactional
    public String challenge(@RequestParam String code,
                            HttpServletRequest request,
                            HttpServletResponse response,
                            HttpSession session) {
        Authentication pending = (Authentication) session.getAttribute(MfaAuthenticationSuccessHandler.PENDING_MFA_AUTH);
        if (pending == null) return "redirect:/login";

        UserDetails principal = (UserDetails) pending.getPrincipal();
        var user = userRepository.findByEmail(principal.getUsername()).orElseThrow();

        boolean valid = totpService.isValidCode(user.getTotpSecretRef(), code)
                || recoveryCodeService.consumeRecoveryCode(user, code);

        if (!valid) {
            auditService.log("MFA_FAILURE", principal.getUsername(), request);
            return "redirect:/mfa/challenge?error";
        }

        loginTrackingService.recordSuccess(principal.getUsername());
        auditService.log("MFA_SUCCESS", principal.getUsername(), request);

        session.removeAttribute(MfaAuthenticationSuccessHandler.PENDING_MFA_AUTH);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(pending);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        return "redirect:/";
    }

    // ── Enrollment ───────────────────────────────────────────────────────────

    @GetMapping("/enroll")
    public String enrollForm(@AuthenticationPrincipal UserDetails principal,
                             Model model, HttpSession session) {
        String secret = totpService.generateSecret();
        session.setAttribute(PENDING_TOTP_SECRET, secret);
        model.addAttribute("otpauthUri", totpService.generateOtpauthUri(secret, principal.getUsername()));
        model.addAttribute("secret", secret);
        return "mfa/enroll";
    }

    @PostMapping("/enroll/confirm")
    @Transactional
    public String enrollConfirm(@RequestParam String code,
                                @AuthenticationPrincipal UserDetails principal,
                                HttpSession session,
                                RedirectAttributes flash) {
        String secret = (String) session.getAttribute(PENDING_TOTP_SECRET);
        if (secret == null) return "redirect:/mfa/enroll";
        if (!totpService.isValidCode(secret, code)) return "redirect:/mfa/enroll?error";

        var user = userRepository.findByEmail(principal.getUsername()).orElseThrow();
        user.setTotpSecretRef(secret);
        user.setMfaEnabled(true);
        userRepository.save(user);
        session.removeAttribute(PENDING_TOTP_SECRET);

        List<String> codes = recoveryCodeService.generateAndStore(user);
        flash.addFlashAttribute("recoveryCodes", codes);
        return "redirect:/mfa/recovery-codes";
    }

    // ── Recovery codes (shown once after enrollment) ─────────────────────────

    @GetMapping("/recovery-codes")
    public String recoveryCodes(Model model) {
        if (!model.containsAttribute("recoveryCodes")) return "redirect:/";
        return "mfa/recovery-codes";
    }
}
