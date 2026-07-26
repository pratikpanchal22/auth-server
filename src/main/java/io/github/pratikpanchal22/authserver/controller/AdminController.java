package io.github.pratikpanchal22.authserver.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.pratikpanchal22.authserver.domain.AuditEvent;
import io.github.pratikpanchal22.authserver.domain.AuthType;
import io.github.pratikpanchal22.authserver.domain.IdentityProvider;
import io.github.pratikpanchal22.authserver.domain.User;
import io.github.pratikpanchal22.authserver.dto.ClientForm;
import io.github.pratikpanchal22.authserver.dto.IdpForm;
import io.github.pratikpanchal22.authserver.dto.UserForm;
import io.github.pratikpanchal22.authserver.repository.AuditEventRepository;
import io.github.pratikpanchal22.authserver.repository.IdentityProviderRepository;
import io.github.pratikpanchal22.authserver.repository.MfaRecoveryCodeRepository;
import io.github.pratikpanchal22.authserver.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final IdentityProviderRepository idpRepository;
    private final MfaRecoveryCodeRepository recoveryCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final AuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper;
    private final RegisteredClientRepository clientRepository;

    public AdminController(UserRepository userRepository,
                           IdentityProviderRepository idpRepository,
                           MfaRecoveryCodeRepository recoveryCodeRepository,
                           PasswordEncoder passwordEncoder,
                           JdbcTemplate jdbcTemplate,
                           AuditEventRepository auditEventRepository,
                           ObjectMapper objectMapper,
                           RegisteredClientRepository clientRepository) {
        this.userRepository = userRepository;
        this.idpRepository = idpRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
        this.auditEventRepository = auditEventRepository;
        this.objectMapper = objectMapper;
        this.clientRepository = clientRepository;
    }

    public record AuditRow(Instant createdAt, String eventType, String email,
                           String ipAddress, String userAgent) {}


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
        user.setMfaRequired(form.isMfaRequired());
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
        form.setMfaRequired(user.isMfaRequired());
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
        user.setMfaRequired(form.isMfaRequired());
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
            user.setTotpFailedAttempts(0);
            userRepository.save(user);
            recoveryCodeRepository.deleteByUser_Id(id);
        });
        ra.addFlashAttribute("success", "MFA reset");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/unlock-totp")
    @Transactional
    public String unlockTotp(@PathVariable UUID id, RedirectAttributes ra) {
        userRepository.findById(id).ifPresent(user -> {
            user.setTotpFailedAttempts(0);
            userRepository.save(user);
        });
        ra.addFlashAttribute("success", "TOTP lockout cleared");
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

    // ==================== Audit Log ====================

    @GetMapping("/audit")
    public String audit(Model model) {
        List<AuditEvent> events = auditEventRepository.findAll(
                PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).getContent();

        List<AuditRow> rows = events.stream()
                .map(e -> new AuditRow(
                        e.getCreatedAt(),
                        e.getEventType(),
                        extractEmail(e.getMetadata()),
                        e.getIpAddress(),
                        e.getUserAgent()))
                .toList();

        model.addAttribute("rows", rows);
        return "admin/audit";
    }

    private String extractEmail(String metadata) {
        if (metadata == null || metadata.isBlank()) return null;
        try {
            JsonNode node = objectMapper.readTree(metadata);
            return node.path("email").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== OAuth Clients ====================

    @GetMapping("/clients")
    public String clients(Model model) {
        model.addAttribute("clients", registeredClients());
        return "admin/clients";
    }

    @GetMapping("/clients/new")
    public String newClient(Model model) {
        model.addAttribute("form", new ClientForm());
        model.addAttribute("editMode", false);
        return "admin/client-form";
    }

    @PostMapping("/clients")
    public String createClient(@ModelAttribute("form") ClientForm form, RedirectAttributes ra) {
        if (clientRepository.findByClientId(form.getClientId()) != null) {
            ra.addFlashAttribute("error", "Client ID already exists: " + form.getClientId());
            return "redirect:/admin/clients/new";
        }
        clientRepository.save(buildClient(UUID.randomUUID().toString(), form, null));
        ra.addFlashAttribute("success", "Client \"" + form.getClientId() + "\" created");
        return "redirect:/admin/clients";
    }

    @GetMapping("/clients/{id}/edit")
    public String editClient(@PathVariable String id, Model model) {
        RegisteredClient client = clientRepository.findById(id);
        if (client == null) return "redirect:/admin/clients";

        ClientForm form = new ClientForm();
        form.setClientId(client.getClientId());
        form.setRedirectUri(client.getRedirectUris().stream().findFirst().orElse(""));
        form.setPostLogoutRedirectUri(client.getPostLogoutRedirectUris().stream().findFirst().orElse(""));
        form.setScopes(new HashSet<>(client.getScopes()));
        form.setAccessTokenTtlMinutes((int) client.getTokenSettings().getAccessTokenTimeToLive().toMinutes());
        form.setRefreshTokenTtlHours((int) client.getTokenSettings().getRefreshTokenTimeToLive().toHours());
        form.setRequireConsent(client.getClientSettings().isRequireAuthorizationConsent());

        model.addAttribute("form", form);
        model.addAttribute("clientInternalId", id);
        model.addAttribute("editMode", true);
        return "admin/client-form";
    }

    @PostMapping("/clients/{id}")
    public String updateClient(@PathVariable String id,
                               @ModelAttribute("form") ClientForm form,
                               RedirectAttributes ra) {
        RegisteredClient existing = clientRepository.findById(id);
        if (existing == null) return "redirect:/admin/clients";

        // Preserve secret if not provided
        String secret = (form.getClientSecret() != null && !form.getClientSecret().isBlank())
                ? passwordEncoder.encode(form.getClientSecret())
                : existing.getClientSecret();

        clientRepository.save(buildClient(id, form, secret));
        ra.addFlashAttribute("success", "Client \"" + form.getClientId() + "\" updated");
        return "redirect:/admin/clients";
    }

    @PostMapping("/clients/{id}/delete")
    public String deleteClient(@PathVariable String id, RedirectAttributes ra) {
        RegisteredClient client = clientRepository.findById(id);
        if (client != null) {
            jdbcTemplate.update("DELETE FROM oauth2_registered_client WHERE id = ?", id);
            ra.addFlashAttribute("success", "Client \"" + client.getClientId() + "\" deleted");
        }
        return "redirect:/admin/clients";
    }

    private RegisteredClient buildClient(String id, ClientForm form, String encodedSecret) {
        String secret = encodedSecret != null
                ? encodedSecret
                : passwordEncoder.encode(form.getClientSecret());

        RegisteredClient.Builder builder = RegisteredClient.withId(id)
                .clientId(form.getClientId())
                .clientSecret(secret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(form.isRequireConsent())
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(form.getAccessTokenTtlMinutes()))
                        .refreshTokenTimeToLive(Duration.ofHours(form.getRefreshTokenTtlHours()))
                        .reuseRefreshTokens(false)
                        .build());

        if (form.getRedirectUri() != null && !form.getRedirectUri().isBlank())
            builder.redirectUri(form.getRedirectUri());
        if (form.getPostLogoutRedirectUri() != null && !form.getPostLogoutRedirectUri().isBlank())
            builder.postLogoutRedirectUri(form.getPostLogoutRedirectUri());

        Set<String> scopes = form.getScopes() != null ? form.getScopes() : Set.of(OidcScopes.OPENID);
        scopes.forEach(builder::scope);

        return builder.build();
    }

    private List<Map<String, Object>> registeredClients() {
        return jdbcTemplate.queryForList(
                "SELECT id, client_id, scopes, redirect_uris, post_logout_redirect_uris, client_id_issued_at " +
                "FROM oauth2_registered_client ORDER BY client_id");
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
