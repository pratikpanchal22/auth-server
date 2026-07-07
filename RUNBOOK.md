# Auth Server — Runbook

This document covers everything needed to build, run, test, and deploy the auth server from scratch. It is updated as new capabilities are added.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Project Structure](#project-structure)
3. [Environment Variables](#environment-variables)
4. [Running Locally](#running-locally)
5. [Running Tests](#running-tests)
6. [Docker](#docker)
7. [AWS Deploy](#aws-deploy)
8. [Verification Checklist](#verification-checklist)
9. [Troubleshooting](#troubleshooting)

---

## Prerequisites

| Tool | Version | Install |
|---|---|---|
| Java | 21 | `brew install --cask temurin@21` |
| Docker Desktop | Latest | https://www.docker.com/products/docker-desktop |
| AWS CLI | v2 | `brew install awscli` (prod only) |

Maven Wrapper (`./mvnw`) is included — no separate Maven install needed.

Verify your setup:

```bash
java -version      # openjdk 21.x.x
docker info        # must show server info — Docker Desktop must be running
```

---

## Project Structure

```
auth-server/
  src/
    main/
      java/io/github/pratikpanchal22/authserver/
        AuthServerApplication.java
        config/
          AuthorizationServerConfig.java    ← Spring Authorization Server (Order 1 chain)
          SecurityConfig.java               ← App security (form login, MFA, admin) (Order 2 chain)
          MfaAuthenticationSuccessHandler.java ← Post-password gate: routes to MFA challenge or saves request
          AuditLogoutSuccessHandler.java    ← Audit log on logout
          LoginFailureHandler.java          ← Audit log on login failure
          DatabaseClientRegistrationRepository.java ← Upstream IDPs from DB
          ClientDataLoader.java             ← Seeds storefront client row (local-dev)
          ProdClientDataLoader.java         ← Skips seed in prod (reads from DB)
          OidcTokenCustomizer.java          ← Adds roles/email to ID/access tokens
          RateLimitConfig.java / RateLimitFilter.java ← Bucket4j rate limiting
          AuthModelAdvice.java              ← Injects nav model attributes globally
        controller/
          LoginController.java              ← GET /login
          HrdController.java                ← GET /hrd/lookup
          MfaController.java                ← /mfa/challenge, /mfa/enroll, /mfa/recovery-codes
          AccountController.java            ← /account (self-service MFA)
          AdminController.java              ← /admin/** (user/IDP/client management)
          HomeController.java               ← GET /
        domain/
          User.java                         ← users table
          IdentityProvider.java             ← identity_providers table
          MfaRecoveryCode.java              ← mfa_recovery_codes table
          AuditEvent.java                   ← audit_events table
          AuthType.java                     ← enum: LOCAL | FEDERATED
        repository/
          UserRepository.java
          IdentityProviderRepository.java
          MfaRecoveryCodeRepository.java
          AuditEventRepository.java
        security/
          ClientAccessFilter.java           ← Per-user client access control (Order 1 chain)
          MfaEnrollmentFilter.java          ← Enforces MFA enrollment when mfa_required=true
        service/
          JitOidcUserService.java           ← JIT provisions federated users
          TotpService.java                  ← TOTP generate/validate
          RecoveryCodeService.java          ← BCrypt-hashed recovery codes
          AuditService.java                 ← Async audit logging
          LoginTrackingService.java         ← Login success/failure tracking
          HrdService.java                   ← Home Realm Discovery logic
          UserDetailsServiceImpl.java       ← Spring Security UserDetailsService
          SecretsService.java               ← AWS Secrets Manager ARN resolution
        dto/
          UserForm.java / IdpForm.java      ← Admin form binding
      resources/
        application.properties              ← Common config (port 9000, actuator)
        application-local-dev.properties    ← Local dev (Docker PostgreSQL, debug logging)
        application-dev.properties          ← Shared dev environment
        application-prd.properties          ← Production (all sensitive values from env vars)
        db/migration/
          V1__create_users.sql
          V2__create_user_roles.sql
          V3__create_identity_providers.sql
          V4__create_mfa_recovery_codes.sql
          V5__create_audit_events.sql
          V6__create_indexes.sql
          V7__seed_admin_user.sql           ← admin@localhost / changeme
          V8__create_oauth2_registered_client.sql  ← Spring Authorization Server JDBC schema
          V9__add_email_domains_to_identity_providers.sql
          V10__add_user_client_access.sql   ← Per-user client access control
          V11__add_login_tracking.sql       ← Login success/failure tracking columns
          V12__totp_lockout_and_refresh_rotation.sql
          V13__add_mfa_required.sql         ← Admin-enforced MFA flag
        templates/
          login.html                        ← Email-first HRD login form
          mfa/challenge.html / enroll.html / recovery-codes.html
          account.html
          admin/                            ← Admin UI templates
          fragments/nav.html                ← Shared navbar fragment
  compose.yaml      ← PostgreSQL 15 for local dev
  Dockerfile        ← Multi-stage build (Maven → JRE)
  pom.xml
  RUNBOOK.md        ← this file
```

---

## Environment Variables

| Variable | Required in | Default (local-dev) | Description |
|---|---|---|---|
| `DB_PASSWORD` | All | `localpassword` | PostgreSQL password |
| `DB_URL` | Prod | — | JDBC URL e.g. `jdbc:postgresql://host:5432/auth_db` |
| `DB_USER` | Prod | — | PostgreSQL username |
| `AUTH_SERVER_BASE_URL` | Prod | `http://localhost:9000` | Public URL — used in OIDC issuer claim |
| `AUTH_SERVER_SIGNING_KEY_REF` | Planned | — | AWS Secrets Manager ARN for RSA-2048 private key |
| `MFA_ENCRYPTION_KEY_REF` | Planned | — | AWS Secrets Manager ARN for AES-GCM encryption key |

In production, all variables are set as EC2 environment variables via the systemd unit file at `/etc/systemd/system/auth-server.service`.

---

## Running Locally

The auth server runs on **port 9000**. PostgreSQL runs on 5432 (Docker).

### Option A — Native app + Docker PostgreSQL (recommended)

**Step 1 — Start Docker Desktop**

```bash
open -a Docker
```

**Step 2 — Start PostgreSQL**

```bash
docker compose up postgresql -d
docker compose ps   # wait until healthy
```

**Step 3 — Run the app**

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local-dev
```

Expected:
```
Started AuthServerApplication in X.XXX seconds
Tomcat started on port 9000
```

**Step 4 — Verify**

```bash
curl http://localhost:9000/actuator/health
# {"status":"UP"}

open http://localhost:9000/login
# Email-first login form
```

Flyway applies all V1–V13 migrations automatically. Seed admin:

| Field | Value |
|---|---|
| Email | `admin@localhost` |
| Password | `changeme` |
| Role | `ADMIN` |

> Change this password in production.

**Stopping**

```bash
docker compose down       # stop PostgreSQL, keep volume
docker compose down -v    # stop PostgreSQL, delete volume (fresh DB)
```

### Option B — Fully containerised

```bash
docker compose up --build
```

---

## Running Tests

```bash
./mvnw test
```

Unit tests use H2 (no Docker needed). Repository tests use Testcontainers (Docker required).

---

## Docker

The `Dockerfile` uses a two-stage build:
1. **Build stage** (`maven:3.9-eclipse-temurin-21-alpine`) — compiles the JAR
2. **Runtime stage** (`eclipse-temurin:21-jre-alpine`) — minimal JRE, ~200 MB

---

## AWS Deploy

### Release lifecycle

This project does **not** deploy to any specific infrastructure from its CI. Instead:

1. A maintainer tags a release: `git tag v1.2.3 && git push origin v1.2.3`
2. The `release.yml` workflow builds and publishes:
   - **JAR** — attached to the GitHub Release at `github.com/pratikpanchal22/auth-server/releases`
   - **Docker image** — pushed to `ghcr.io/pratikpanchal22/auth-server:1.2.3`
3. Your infrastructure picks up the artifact and deploys it.

### Deploying from a GitHub Release (JAR on EC2 + systemd)

```bash
VERSION=v1.2.3

# Download JAR from GitHub Release (no auth required for public repo)
curl -L -o /opt/auth-server/app.jar \
  "https://github.com/pratikpanchal22/auth-server/releases/download/${VERSION}/auth-server-0.0.1-SNAPSHOT.jar"

chown appuser:appuser /opt/auth-server/app.jar
systemctl restart auth-server
journalctl -u auth-server -f
```

Or automate this in your own infrastructure repository — trigger it on new releases using GitHub's `repository_dispatch` event or a `workflow_dispatch` with a `version` input.

### Deploying from GHCR (Docker)

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
  ghcr.io/pratikpanchal22/auth-server:${VERSION}
```

### EC2 first-time setup (JAR + systemd)

**1. Install Java 21**
```bash
sudo dnf install -y java-21-amazon-corretto-headless   # Amazon Linux 2023
# or: sudo apt install -y temurin-21-jre              # Ubuntu
```

**2. Create service account and directory**
```bash
sudo useradd -r -s /sbin/nologin appuser
sudo mkdir -p /opt/auth-server
sudo chown appuser:appuser /opt/auth-server
```

**3. Create systemd unit** at `/etc/systemd/system/auth-server.service`:
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
Environment="STOREFRONT_BASE_URL=https://your-app.example.com"
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable auth-server
```

**4. Configure Nginx** — terminate TLS and proxy to port 9000:
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

---

## Verification Checklist

### Basic health

- [ ] `curl https://auth.example.com/actuator/health` → `{"status":"UP"}`
- [ ] `curl https://auth.example.com/.well-known/openid-configuration` → JSON with `issuer`, `authorization_endpoint`, `end_session_endpoint`, etc.
- [ ] `curl https://auth.example.com/oauth2/jwks` → JSON with RSA public key

### Login flows

- [ ] `https://auth.example.com/login` → email-first form renders
- [ ] Enter `@gmail.com` address → routes to Google OIDC (if Google IDP seeded)
- [ ] Enter `admin@localhost` → password field revealed
- [ ] Log in as `admin@localhost` / `changeme` → redirects to `/`

### MFA

- [ ] Visit `/mfa/enroll` when logged in → QR code + manual key displayed
- [ ] Scan with authenticator app, enter code → enrollment confirmed, recovery codes shown
- [ ] Log out, log back in → TOTP challenge presented
- [ ] Enter valid TOTP code → login completes

### Logout (RP-Initiated Logout)

- [ ] Log into storefront at `https://your-app.example.com`
- [ ] Click Logout → browser visits `https://auth.example.com/connect/logout?id_token_hint=...`
- [ ] Auth-server clears session → redirects to `https://your-app.example.com/`
- [ ] Click Sign In again → auth-server shows login form (no silent re-authentication)

### Admin UI

- [ ] Log in as admin → `/admin/users` lists users
- [ ] Create/edit/disable a user
- [ ] Require MFA for a user → after next login, user is redirected to `/mfa/enroll`
- [ ] `/admin/identity-providers` → list and manage upstream IDPs
- [ ] `/admin/clients` → list registered client applications

### OAuth2 client (storefront)

- [ ] Visit `https://your-app.example.com/` → redirects to auth-server (unauthenticated)
- [ ] Log in → redirected back to storefront with active session
- [ ] Refresh page → session persists

---

## Troubleshooting

### Docker daemon not running

```
Cannot connect to the Docker daemon
```

Start Docker Desktop: `open -a Docker`, wait ~30 seconds.

---

### Port 9000 already in use

```bash
lsof -ti:9000 | xargs kill -9
```

---

### PostgreSQL container not healthy

```bash
docker compose logs postgresql
docker compose down -v && docker compose up postgresql -d   # fresh volume
```

---

### Wrong Java version

```bash
java -version    # must be 21
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

---

### Flyway checksum mismatch

Never edit applied migrations. To reset local dev DB:

```bash
docker compose down -v
docker compose up postgresql -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=local-dev
```

---

### Auth-server in prod not starting after deploy

```bash
# Check systemd logs directly on the EC2 instance
journalctl -u auth-server -n 100 --no-pager
```

Common causes:
- `DB_URL` / `DB_USER` / `DB_PASSWORD` env vars missing in systemd unit
- Flyway migration error (check logs for `FlywayException`)
- Port 9000 still bound by old process (systemd `restart` should handle this)
