package io.github.pratikpanchal22.authserver.dto;

import java.util.HashSet;
import java.util.Set;

public class ClientForm {

    private String clientId;
    private String redirectUri;
    private String postLogoutRedirectUri;
    private Set<String> scopes = new HashSet<>(Set.of("openid", "profile", "email"));
    private int accessTokenTtlMinutes = 15;
    private int refreshTokenTtlHours = 8;
    private boolean requireConsent = false;

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
}
