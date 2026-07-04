package io.github.pratikpanchal22.authserver.controller;

import io.github.pratikpanchal22.authserver.domain.AuthType;
import io.github.pratikpanchal22.authserver.domain.IdentityProvider;
import io.github.pratikpanchal22.authserver.domain.User;
import io.github.pratikpanchal22.authserver.dto.IdpForm;
import io.github.pratikpanchal22.authserver.dto.UserForm;
import io.github.pratikpanchal22.authserver.repository.IdentityProviderRepository;
import io.github.pratikpanchal22.authserver.repository.MfaRecoveryCodeRepository;
import io.github.pratikpanchal22.authserver.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final IdentityProviderRepository idpRepository;
    private final MfaRecoveryCodeRepository recoveryCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    public AdminController(UserRepository userRepository,
                           IdentityProviderRepository idpRepository,
                           MfaRecoveryCodeRepository recoveryCodeRepository,
                           PasswordEncoder passwordEncoder,
                           JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.idpRepository = idpRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping({"", "/"})
    public String root() {
        return "redirect:/admin/users";
    }

    // ==================== Users ====================

    @GetMapping("/users")
    public String users(Model model) {
        model.addAttribute("users", userRepository.findAll(Sort.by("email")));
        return "admin/users";
    }

    @GetMapping("/users/new")
    public String newUser(Model model) {
        model.addAttribute("form", new UserForm());
        model.addAttribute("editMode", false);
        model.addAttribute("registeredClients", registeredClients());
        return "admin/user-form";
    }

    @PostMapping("/users")
    public String createUser(@ModelAttribute("form") UserForm form, RedirectAttributes ra) {
        if (userRepository.existsByEmail(form.getEmail())) {
            ra.addFlashAttribute("error", "Email already in use: " + form.getEmail());
            return "redirect:/admin/users/new";
        }
        User user = new User();
        user.setEmail(form.getEmail());
        user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        user.setAuthType(AuthType.LOCAL);
        user.setActive(form.isActive());
        user.setRoles(form.getRoles() != null ? form.getRoles() : new HashSet<>());
        user.setAllowedClients(form.getAllowedClients() != null ? form.getAllowedClients() : new HashSet<>());
        userRepository.save(user);
        ra.addFlashAttribute("success", "User " + form.getEmail() + " created");
        return "redirect:/admin/users";
    }

    @GetMapping("/users/{id}/edit")
    public String editUser(@PathVariable UUID id, Model model) {
        User user = userRepository.findById(id).orElseThrow();
        UserForm form = new UserForm();
        form.setEmail(user.getEmail());
        form.setActive(user.isActive());
        form.setRoles(new HashSet<>(user.getRoles()));
        form.setAllowedClients(new HashSet<>(user.getAllowedClients()));
        model.addAttribute("form", form);
        model.addAttribute("userId", id);
        model.addAttribute("mfaEnabled", user.isMfaEnabled());
        model.addAttribute("editMode", true);
        model.addAttribute("registeredClients", registeredClients());
        return "admin/user-form";
    }

    @PostMapping("/users/{id}")
    @Transactional
    public String updateUser(@PathVariable UUID id,
                             @ModelAttribute("form") UserForm form,
                             RedirectAttributes ra) {
        User user = userRepository.findById(id).orElseThrow();
        user.setActive(form.isActive());
        user.setRoles(form.getRoles() != null ? form.getRoles() : new HashSet<>());
        user.setAllowedClients(form.getAllowedClients() != null ? form.getAllowedClients() : new HashSet<>());
        if (form.getPassword() != null && !form.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        }
        userRepository.save(user);
        ra.addFlashAttribute("success", "User updated");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/delete")
    public String deleteUser(@PathVariable UUID id, RedirectAttributes ra) {
        userRepository.deleteById(id);
        ra.addFlashAttribute("success", "User deleted");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/reset-mfa")
    @Transactional
    public String resetMfa(@PathVariable UUID id, RedirectAttributes ra) {
        userRepository.findById(id).ifPresent(user -> {
            user.setMfaEnabled(false);
            user.setTotpSecretRef(null);
            userRepository.save(user);
            recoveryCodeRepository.deleteByUser_Id(id);
        });
        ra.addFlashAttribute("success", "MFA reset");
        return "redirect:/admin/users";
    }

    // ==================== IDPs ====================

    @GetMapping("/idps")
    public String idps(Model model) {
        model.addAttribute("idps", idpRepository.findAll(Sort.by("name")));
        return "admin/idps";
    }

    @GetMapping("/idps/new")
    public String newIdp(Model model) {
        model.addAttribute("form", new IdpForm());
        model.addAttribute("editMode", false);
        return "admin/idp-form";
    }

    @PostMapping("/idps")
    public String createIdp(@ModelAttribute("form") IdpForm form, RedirectAttributes ra) {
        IdentityProvider idp = new IdentityProvider();
        idp.setName(form.getName());
        idp.setIssuerUrl(form.getIssuerUrl());
        idp.setClientId(form.getClientId());
        idp.setClientSecretRef(form.getClientSecretRef());
        idp.setScopes(form.getScopes() != null ? form.getScopes() : "openid,profile,email");
        idp.setEmailDomains(blankToNull(form.getEmailDomains()));
        idp.setEnabled(form.isEnabled());
        idpRepository.save(idp);
        ra.addFlashAttribute("success", "Identity provider \"" + idp.getName() + "\" created");
        return "redirect:/admin/idps";
    }

    @GetMapping("/idps/{id}/edit")
    public String editIdp(@PathVariable UUID id, Model model) {
        IdentityProvider idp = idpRepository.findById(id).orElseThrow();
        IdpForm form = new IdpForm();
        form.setName(idp.getName());
        form.setIssuerUrl(idp.getIssuerUrl());
        form.setClientId(idp.getClientId());
        form.setClientSecretRef(idp.getClientSecretRef());
        form.setScopes(idp.getScopes());
        form.setEmailDomains(idp.getEmailDomains());
        form.setEnabled(idp.isEnabled());
        model.addAttribute("form", form);
        model.addAttribute("idpId", id);
        model.addAttribute("editMode", true);
        return "admin/idp-form";
    }

    @PostMapping("/idps/{id}")
    public String updateIdp(@PathVariable UUID id,
                            @ModelAttribute("form") IdpForm form,
                            RedirectAttributes ra) {
        IdentityProvider idp = idpRepository.findById(id).orElseThrow();
        idp.setIssuerUrl(form.getIssuerUrl());
        idp.setClientId(form.getClientId());
        idp.setClientSecretRef(form.getClientSecretRef());
        idp.setScopes(form.getScopes() != null ? form.getScopes() : "openid,profile,email");
        idp.setEmailDomains(blankToNull(form.getEmailDomains()));
        idp.setEnabled(form.isEnabled());
        idpRepository.save(idp);
        ra.addFlashAttribute("success", "Identity provider updated");
        return "redirect:/admin/idps";
    }

    @PostMapping("/idps/{id}/delete")
    public String deleteIdp(@PathVariable UUID id, RedirectAttributes ra) {
        idpRepository.deleteById(id);
        ra.addFlashAttribute("success", "Identity provider deleted");
        return "redirect:/admin/idps";
    }

    // ==================== OAuth Clients ====================

    @GetMapping("/clients")
    public String clients(Model model) {
        model.addAttribute("clients", registeredClients());
        return "admin/clients";
    }

    private List<Map<String, Object>> registeredClients() {
        return jdbcTemplate.queryForList(
                "SELECT client_id, scopes, redirect_uris, client_id_issued_at " +
                "FROM oauth2_registered_client ORDER BY client_id");
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
