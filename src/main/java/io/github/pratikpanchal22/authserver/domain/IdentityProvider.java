package io.github.pratikpanchal22.authserver.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "identity_providers")
@Getter
@Setter
@NoArgsConstructor
public class IdentityProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "issuer_url", nullable = false, length = 500)
    private String issuerUrl;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    /** AWS Secrets Manager ARN — never store plaintext secret here */
    @Column(name = "client_secret_ref", nullable = false, length = 500)
    private String clientSecretRef;

    @Column(nullable = false, length = 500)
    private String scopes = "openid,profile,email";

    /** Comma-separated email domains routed to this IDP (e.g. "gmail.com,googlemail.com"). Null = not domain-routed. */
    @Column(name = "email_domains", length = 500)
    private String emailDomains;

    @Column(nullable = false)
    private boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
