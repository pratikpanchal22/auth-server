package io.github.pratikpanchal22.authserver.dto;

import java.util.HashSet;
import java.util.Set;

public class UserForm {
    private String email;
    private String password;
    private boolean active = true;
    private boolean mfaRequired = false;
    private Set<String> roles = new HashSet<>();
    private Set<String> allowedClients = new HashSet<>();

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public boolean isMfaRequired() { return mfaRequired; }
    public void setMfaRequired(boolean mfaRequired) { this.mfaRequired = mfaRequired; }

    public Set<String> getRoles() { return roles; }
    public void setRoles(Set<String> roles) { this.roles = roles; }

    public Set<String> getAllowedClients() { return allowedClients; }
    public void setAllowedClients(Set<String> allowedClients) { this.allowedClients = allowedClients; }
}
