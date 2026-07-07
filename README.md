# Auth Server

A self-hosted OIDC Authorization Server built with Spring Boot 3.5 and Spring Authorization Server 1.5. Runs as a standalone service that acts as the single identity provider for all your applications.

![Java 21](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5.7-6DB33F?logo=spring)
![Spring Authorization Server](https://img.shields.io/badge/Spring%20Authorization%20Server-1.5-6DB33F?logo=spring)
![PostgreSQL 15](https://img.shields.io/badge/PostgreSQL-15-4169E1?logo=postgresql&logoColor=white)

---

## Overview

Instead of each application managing its own users and login pages, every client app delegates authentication to this server via the **OIDC authorization code flow**. The auth server handles:

- Local users — email + BCrypt password + TOTP MFA
- Federated users — upstream OIDC IDPs (Google, Okta, Azure AD, etc.) via Home Realm Discovery
- Token issuance — ID tokens, access tokens, refresh tokens (RS256 JWTs)
- Session management — RP-Initiated Logout clears the auth-server session
- Admin UI — manage users, identity providers, and registered clients without redeployment

---

## System Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                     Auth Server :9000                        │
│                                                              │
│  ┌──────────────────┐  ┌──────────────────┐  ┌───────────┐  │
│  │  Local Auth      │  │  Home Realm       │  │  Upstream │  │
│  │  BCrypt + TOTP   │  │  Discovery (HRD)  │  │  IDP      │  │
│  └──────────────────┘  └──────────────────┘  │  Bridge   │  │
│           │                    │              └───────────┘  │
│      PostgreSQL            login.html              │         │
│   (users, tokens,                           Google / Okta    │
│    clients, IDPs)                           Azure AD / ADFS  │
└──────────────────────────────────────────────────────────────┘
              │
              │  OIDC — authorization_code + PKCE
              │  Discovery: /.well-known/openid-configuration
              │
              ├──────────────────────►  Your web app (app.example.com)
              ├──────────────────────►  Calibre-Web (books.example.com)
              └──────────────────────►  Any OIDC-capable app
```

### Filter chain

Two Spring Security filter chains run in priority order:

| Order | Chain | Handles |
|-------|-------|---------|
| 1 | Authorization Server | `/oauth2/**`, `/connect/**`, `/.well-known/**` |
| 2 | Application | Login, MFA, admin, account pages |

`ClientAccessFilter` (Order 1) enforces per-user client access control.  
`MfaEnrollmentFilter` (Order 2) gates users with `mfa_required = true` until enrolled.  
`RateLimitFilter` applies Bucket4j limits to `/login` and `/hrd/lookup`.

### Login flow

```
Browser → GET /oauth2/authorize (from client app)
        ↓
Auth Server — user not authenticated
        ↓
GET /login  →  email-first form
        ↓
GET /hrd/lookup?email=...
        ├── FEDERATED domain → 302 upstream IDP → JIT provision on callback
        └── LOCAL → reveal password field → POST /login
                        ↓
                  MFA enabled?
                  ├── yes → store pending auth, 302 /mfa/challenge
                  └── no  → issue authorization code → 302 back to client
```

### Token strategy

| Token | Format | Lifetime |
|-------|--------|----------|
| ID Token | RS256 JWT | 10 min |
| Access Token | RS256 JWT | 15 min |
| Refresh Token | Opaque (DB-stored, rotated on use) | 8 h |
| Authorization Code | Opaque, single-use | 60 s |

---

## Quick Start — Local Development

### Prerequisites

| Tool | Version |
|------|---------|
| Java | 21 |
| Docker Desktop | Latest |

```bash
java -version   # openjdk 21
docker info     # Docker must be running
```

### Option A — Native app + Docker PostgreSQL (fastest dev loop)

```bash
# 1. Clone
git clone https://github.com/pratikpanchal22/auth-server.git
cd auth-server

# 2. Start PostgreSQL
docker compose up postgresql -d

# 3. Run the app
./mvnw spring-boot:run -Dspring-boot.run.profiles=local-dev
```

Visit `http://localhost:9000/login`.  
Log in as **admin@localhost** / **changeme**.

Flyway applies all migrations automatically on startup. To reset the database:

```bash
docker compose down -v   # wipe volume
docker compose up postgresql -d
```

### Option B — Fully containerised

```bash
docker compose up --build
```

Both PostgreSQL and the auth server start together. Verify:

```bash
curl http://localhost:9000/actuator/health
# {"status":"UP"}
```

### Connecting a client app locally

Point your client app's OAuth2 settings at:

| Setting | Value |
|---------|-------|
| Issuer / Discovery URL | `http://localhost:9000` |
| Authorization endpoint | `http://localhost:9000/oauth2/authorize` |
| Token endpoint | `http://localhost:9000/oauth2/token` |
| JWKS endpoint | `http://localhost:9000/oauth2/jwks` |
| UserInfo endpoint | `http://localhost:9000/userinfo` |

The `local-dev` profile seeds a `storefront` client row automatically (see `ClientDataLoader`). For a new client, insert a row via the admin UI at `http://localhost:9000/admin/clients`.

### Adding a Google IDP locally

1. Create an OAuth 2.0 credential at [console.cloud.google.com](https://console.cloud.google.com) — set the redirect URI to `http://localhost:9000/login/oauth2/code/google`.
2. Insert the IDP row:

```sql
INSERT INTO identity_providers
  (name, issuer_url, client_id, client_secret_ref, scopes, email_domains, enabled)
VALUES
  ('google', 'https://accounts.google.com',
   '<CLIENT_ID>', '<CLIENT_SECRET>',
   'openid,profile,email', 'gmail.com,googlemail.com', true);
```

3. Visit `http://localhost:9000/login` and enter a `@gmail.com` address — it routes to Google automatically.

---

## Running Tests

No database required for unit tests. Testcontainers spins up PostgreSQL for integration tests (Docker must be running).

```bash
./mvnw test
```

---

## Endpoints

### OIDC / OAuth2 protocol (Spring Authorization Server)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/.well-known/openid-configuration` | OIDC discovery document |
| `GET` | `/oauth2/authorize` | Authorization endpoint — start login flow |
| `POST` | `/oauth2/token` | Token exchange (code → tokens, refresh) |
| `GET` | `/oauth2/jwks` | Public key set for token validation |
| `GET/POST` | `/oauth2/introspect` | Token introspection |
| `GET/POST` | `/oauth2/revoke` | Token revocation |
| `GET/POST` | `/userinfo` | UserInfo endpoint (requires Bearer token) |
| `GET` | `/connect/logout` | RP-Initiated Logout — clears session + redirects to `post_logout_redirect_uri` |

### Authentication

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/login` | Public | Email-first login form |
| `POST` | `/login` | Public | Spring Security form login |
| `GET` | `/hrd/lookup` | Public | Home Realm Discovery — returns `{ authType, registrationId }` for an email |
| `POST` | `/logout` | Authenticated | Logs out of auth server session |
| `GET` | `/access-denied` | Public | Error page for forbidden requests |

### MFA

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/mfa/challenge` | `PRE_MFA` | TOTP challenge form |
| `POST` | `/mfa/challenge` | `PRE_MFA` | Submit TOTP code or recovery code |
| `GET` | `/mfa/enroll` | Authenticated | TOTP enrollment — shows QR code |
| `POST` | `/mfa/enroll/confirm` | Authenticated | Confirm enrollment with first code |
| `GET` | `/mfa/recovery-codes` | Authenticated | View recovery codes (shown once after enrollment) |

### Account (self-service)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/account` | Authenticated | Profile card + MFA status |
| `POST` | `/account/mfa/disable` | Authenticated | Remove MFA enrollment (blocked if admin-required) |

### Admin (ADMIN role required)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/admin/users` | List all users |
| `GET/POST` | `/admin/users/new` | Create user |
| `GET/POST` | `/admin/users/{id}/edit` | Edit user (roles, active, MFA required) |
| `POST` | `/admin/users/{id}/delete` | Delete user |
| `POST` | `/admin/users/{id}/reset-mfa` | Clear MFA enrollment |
| `POST` | `/admin/users/{id}/unlock-totp` | Clear TOTP lockout |
| `GET` | `/admin/idps` | List identity providers |
| `GET/POST` | `/admin/idps/new` | Register upstream IDP |
| `GET/POST` | `/admin/idps/{id}/edit` | Edit IDP |
| `POST` | `/admin/idps/{id}/delete` | Delete IDP |
| `GET` | `/admin/clients` | List registered OAuth2 clients |
| `GET` | `/admin/audit` | Audit event log |

### Health / Ops

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/actuator/health` | Public | `{"status":"UP"}` |
| `GET` | `/actuator/info` | Public | App name + version |

---

## Configuration

### Spring profiles

| Profile | Use |
|---------|-----|
| `local-dev` | Laptop development — Docker PostgreSQL, Spring Security DEBUG logging |
| `dev` | Shared dev environment |
| `stg` | Staging |
| `prd` | Production — all sensitive values from environment variables |

### Environment variables

| Variable | Required | Description |
|----------|----------|-------------|
| `DB_URL` | Prod | JDBC URL e.g. `jdbc:postgresql://host:5432/auth_db` |
| `DB_USER` | Prod | PostgreSQL username |
| `DB_PASSWORD` | All | PostgreSQL password (default `localpassword` in local-dev) |
| `AUTH_SERVER_BASE_URL` | Prod | Public URL — used as OIDC issuer claim e.g. `https://auth.example.com` |
| `AUTH_SERVER_SIGNING_KEY_REF` | Planned | AWS Secrets Manager ARN for RSA-2048 signing key |
| `MFA_ENCRYPTION_KEY_REF` | Planned | AWS Secrets Manager ARN for AES-GCM TOTP encryption key |

---

## Cloud Deployment

### Release lifecycle

This project publishes versioned artifacts — your infrastructure decides when and how to deploy them.

```
Maintainer tags a release
  git tag v1.2.3 && git push origin v1.2.3
          │
          ▼
  GitHub Actions (release.yml)
    ├── Runs tests
    ├── Builds JAR
    ├── Creates GitHub Release with JAR attached
    │     github.com/pratikpanchal22/auth-server/releases/tag/v1.2.3
    └── Builds + pushes Docker image to GHCR
          ghcr.io/pratikpanchal22/auth-server:1.2.3
          ghcr.io/pratikpanchal22/auth-server:latest

Your infrastructure (private repo or manual step)
  └── Picks up v1.2.3 and deploys to your servers
```

No AWS credentials or deployment logic live in this repository.

### Deploying the JAR (EC2 + systemd)

```bash
VERSION=v1.2.3

# Download from GitHub Release (public repo — no auth required)
curl -L -o /opt/auth-server/app.jar \
  "https://github.com/pratikpanchal22/auth-server/releases/download/${VERSION}/auth-server-0.0.1-SNAPSHOT.jar"

chown appuser:appuser /opt/auth-server/app.jar
systemctl restart auth-server
journalctl -u auth-server -f
```

Automate this in your own infrastructure repository — listen for new releases via GitHub's `repository_dispatch` event or a `workflow_dispatch` with a `version` input.

### Deploying the Docker image

```bash
VERSION=v1.2.3

docker pull ghcr.io/pratikpanchal22/auth-server:${VERSION}
docker stop auth-server || true
docker run -d --name auth-server \
  -p 9000:9000 \
  -e SPRING_PROFILES_ACTIVE=prd \
  -e DB_URL=jdbc:postgresql://host:5432/auth_db \
  -e DB_USER=auth_user \
  -e DB_PASSWORD=<secret> \
  -e AUTH_SERVER_BASE_URL=https://auth.example.com \
  -e STOREFRONT_BASE_URL=https://your-app.example.com \
  ghcr.io/pratikpanchal22/auth-server:${VERSION}
```

### EC2 setup (first time, JAR + systemd)

**1. Install Java 21**
```bash
sudo dnf install -y java-21-amazon-corretto-headless
```

**2. Create the service account and directory**
```bash
sudo useradd -r -s /sbin/nologin appuser
sudo mkdir -p /opt/auth-server
sudo chown appuser:appuser /opt/auth-server
```

**3. Create the systemd unit** at `/etc/systemd/system/auth-server.service`:
```ini
[Unit]
Description=Auth Server
After=network.target

[Service]
User=appuser
WorkingDirectory=/opt/auth-server
ExecStart=/usr/bin/java -jar /opt/auth-server/app.jar
Environment="SPRING_PROFILES_ACTIVE=prd"
Environment="DB_URL=jdbc:postgresql://host:5432/auth_db"
Environment="DB_USER=auth_user"
Environment="DB_PASSWORD=<secret>"
Environment="AUTH_SERVER_BASE_URL=https://auth.example.com"
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable auth-server
```

**4. Configure Nginx** — terminate TLS and proxy to port 9000. Forward headers so OAuth2 redirect URIs use HTTPS:

```nginx
location / {
    proxy_pass         http://127.0.0.1:9000;
    proxy_set_header   Host              $host;
    proxy_set_header   X-Forwarded-Proto $scheme;
    proxy_set_header   X-Forwarded-Host  $host;
    proxy_set_header   X-Real-IP         $remote_addr;
}
```

`server.forward-headers-strategy=framework` in `application-prd.properties` tells Spring to trust these headers.

**5. First deploy**
```bash
aws s3 cp s3://your-bucket/app.jar /opt/auth-server/app.jar
sudo chown appuser:appuser /opt/auth-server/app.jar
sudo systemctl start auth-server
journalctl -u auth-server -f
```

Flyway applies all 13 migrations automatically on first startup.

### Verify production health

```bash
curl https://auth.example.com/actuator/health
# {"status":"UP"}

curl https://auth.example.com/.well-known/openid-configuration | jq .issuer
# "https://auth.example.com"
```

---

## Registering a Client Application

1. Log into the admin UI at `/admin/clients`
2. Click **New Client** and fill in:
   - **Client ID** — unique identifier (e.g. `my-app`)
   - **Client Secret** — generated securely; share with the app via an env var
   - **Redirect URIs** — the app's OAuth2 callback URL (e.g. `https://myapp.example.com/login/oauth2/code/auth-server`)
   - **Scopes** — `openid profile email` (at minimum `openid`)
   - **Grant types** — `authorization_code` + `refresh_token`
3. In your client app, point the OIDC settings at this auth server's issuer URL and use the client ID + secret.

---

## Database Schema

Flyway manages all migrations in `src/main/resources/db/migration/`:

| Migration | Table / Change |
|-----------|----------------|
| V1 | `users` |
| V2 | `user_roles` |
| V3 | `identity_providers` |
| V4 | `mfa_recovery_codes` |
| V5 | `audit_events` |
| V6 | Indexes |
| V7 | Seed admin user (`admin@localhost` / `changeme`) |
| V8 | `oauth2_registered_client` (Spring Authorization Server schema) |
| V9 | `email_domains` column on `identity_providers` |
| V10 | `user_client_access` (per-user client access control) |
| V11 | Login tracking columns on `users` |
| V12 | TOTP lockout counter + refresh token rotation support |
| V13 | `mfa_required` column on `users` |

Spring Authorization Server manages `oauth2_authorization` and `oauth2_authorization_consent` automatically alongside the V8 schema.

---

## FAQ

**Q: Can I use this without TOTP MFA?**  
Yes. MFA is per-user opt-in (or admin-enforced). Users without MFA enrolled log in with just email + password.

**Q: How do I reset a locked-out TOTP account?**  
An admin can unlock a user from the admin UI: `/admin/users/{id}/unlock-totp`. This clears the `totp_failed_attempts` counter.

**Q: How do I add a new upstream identity provider?**  
Insert a row in `identity_providers` (directly or via `/admin/idps/new`) with the IDP's `issuer_url`, `client_id`, `client_secret_ref`, and the email domains to route. No restart needed — the auth server loads IDP configs from the database on each request.

**Q: What happens when the RSA signing key is regenerated on restart?**  
Currently the signing key is generated in memory on startup, so restarting the auth server invalidates all previously issued tokens. For production, persist the key in AWS Secrets Manager (set `AUTH_SERVER_SIGNING_KEY_REF`).

**Q: How does the client app know to trust tokens from this server?**  
Client apps fetch the public key from `/oauth2/jwks` and verify the RS256 signature on every JWT. Spring Security OAuth2 Resource Server does this automatically when configured with `jwk-set-uri` or `issuer-uri`.

**Q: Why don't you use `issuer-uri` in the storefront's provider config?**  
Spring Boot's `issuer-uri` triggers a synchronous discovery-document fetch at startup. If the auth server isn't reachable (unit tests, cold CI), the storefront fails to start. Individual endpoint URIs avoid that dependency. See the storefront's `ResearchDocs/pr-history/phase-06-oidc-migration.md` for details.

**Q: How do I trigger a manual deploy without pushing a commit?**  
The workflow supports `workflow_dispatch`. Go to **Actions → Deploy to Production → Run workflow** in the GitHub UI.

**Q: Where are the logs in production?**  
```bash
journalctl -u auth-server -f           # live tail
journalctl -u auth-server -n 200       # last 200 lines
journalctl -u auth-server --since "1 hour ago"
```

---

## Security Notes

| Concern | Mitigation |
|---------|------------|
| IDP client secrets | Referenced via AWS Secrets Manager ARN — never stored in DB plaintext |
| Password storage | BCrypt, cost factor 12 |
| PKCE | Required for all `authorization_code` clients |
| CSRF | Spring Security state parameter on all OAuth2 flows |
| TOTP brute force | Hard lockout after 4 failed attempts; admin unlock required |
| Refresh token theft detection | Single-use rotation — replaying a revoked token kills the session |
| Rate limiting | Bucket4j on `/login` and `/hrd/lookup` |
| Forwarded header trust | Only trusted when `server.forward-headers-strategy=framework` is set |

---

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Framework | Spring Boot 3.5.7 (Java 21) |
| Authorization Server | Spring Authorization Server 1.5 |
| Security | Spring Security 6.x |
| Persistence | Spring Data JPA + PostgreSQL 15 |
| Schema migrations | Flyway |
| TOTP | `dev.samstevens.totp` 1.7.1 |
| Rate limiting | Bucket4j |
| Templates | Thymeleaf + Bootstrap 5 |
| Secrets | AWS Secrets Manager |
| Deploy | GitHub Actions + AWS SSM + systemd |

---

## Contributing

1. Fork the repo and create a feature branch: `git checkout -b feature/my-change`
2. Follow the existing commit style and keep PRs focused
3. All PRs should pass `./mvnw test`
4. Open a pull request against `main`

See `RUNBOOK.md` for full local setup and `ResearchDocs/pr-history/` for the implementation history.
