package io.github.pratikpanchal22.authserver.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "client_ui_metadata")
public class ClientUiMetadata {

    @Id
    @Column(name = "client_id", length = 100)
    private String clientId;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(length = 255)
    private String description;

    @Column(name = "launch_url", nullable = false, length = 500)
    private String launchUrl;

    @Column(nullable = false, length = 50)
    private String icon = "apps";

    @Column(nullable = false)
    private boolean visible = false;

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

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
