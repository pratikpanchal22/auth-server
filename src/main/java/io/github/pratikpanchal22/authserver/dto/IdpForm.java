package io.github.pratikpanchal22.authserver.dto;

public class IdpForm {
    private String name;
    private String issuerUrl;
    private String clientId;
    private String clientSecretRef;
    private String scopes = "openid,profile,email";
    private String emailDomains;
    private boolean enabled = true;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIssuerUrl() { return issuerUrl; }
    public void setIssuerUrl(String issuerUrl) { this.issuerUrl = issuerUrl; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getClientSecretRef() { return clientSecretRef; }
    public void setClientSecretRef(String clientSecretRef) { this.clientSecretRef = clientSecretRef; }

    public String getScopes() { return scopes; }
    public void setScopes(String scopes) { this.scopes = scopes; }

    public String getEmailDomains() { return emailDomains; }
    public void setEmailDomains(String emailDomains) { this.emailDomains = emailDomains; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
