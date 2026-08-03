package io.github.pratikpanchal22.authserver.dto;

import jakarta.validation.constraints.*;
import java.util.HashSet;
import java.util.Set;

public class ClientForm {

    @NotBlank(message = "Client ID is required")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Client ID may only contain lowercase letters, digits, and hyphens")
    @Size(max = 100, message = "Client ID must be 100 characters or fewer")
    private String clientId;

    @Pattern(regexp = "^(https?://.*)?$", message = "Redirect URI must be a valid http/https URL")
    private String redirectUri;

    @Pattern(regexp = "^(https?://.*)?$", message = "Post-logout URI must be a valid http/https URL")
    private String postLogoutRedirectUri;

    @NotEmpty(message = "At least one scope must be selected")
    private Set<String> scopes = new HashSet<>(Set.of("openid", "profile", "email"));

    @Min(value = 1, message = "Access token TTL must be at least 1 minute")
    @Max(value = 1440, message = "Access token TTL must be at most 1440 minutes (24 hours)")
    private int accessTokenTtlMinutes = 15;

    @Min(value = 1, message = "Refresh token TTL must be at least 1 hour")
    @Max(value = 720, message = "Refresh token TTL must be at most 720 hours (30 days)")
    private int refreshTokenTtlHours = 8;

    private boolean requireConsent = false;

    // App Portal metadata — stored in client_ui_metadata, optional
    private String displayName;
    private String description;
    private String launchUrl;
    private String icon = "apps";
    private boolean visible = false;

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }

    public String getPostLogoutRedirectUri() { return postLogoutRedirectUri; }
    public void setPostLogoutRedirectUri(String postLogoutRedirectUri) { this.postLogoutRedirectUri = postLogoutRedirectUri; }

    public Set<String> getScopes() { return scopes; }
    public void setScopes(Set<String> scopes) { this.scopes = scopes != null ? scopes : new HashSet<>(); }

    public int getAccessTokenTtlMinutes() { return accessTokenTtlMinutes; }
    public void setAccessTokenTtlMinutes(int accessTokenTtlMinutes) { this.accessTokenTtlMinutes = accessTokenTtlMinutes; }

    public int getRefreshTokenTtlHours() { return refreshTokenTtlHours; }
    public void setRefreshTokenTtlHours(int refreshTokenTtlHours) { this.refreshTokenTtlHours = refreshTokenTtlHours; }

    public boolean isRequireConsent() { return requireConsent; }
    public void setRequireConsent(boolean requireConsent) { this.requireConsent = requireConsent; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLaunchUrl() { return launchUrl; }
    public void setLaunchUrl(String launchUrl) { this.launchUrl = launchUrl; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }
}
