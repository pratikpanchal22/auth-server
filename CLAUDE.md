# CLAUDE.md

This file provides guidance to Claude Code when working with code in this repository.

## Commands

```bash
# Run locally (PostgreSQL must be running — see RUNBOOK.md)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local-dev

# Run fully containerised
docker compose up --build

# Run tests (no database required)
./mvnw test

# Build JAR
./mvnw clean package -DskipTests

# Start PostgreSQL only (for native dev)
docker compose up postgresql -d
```

Profile conventions:
- `local-dev` — laptop development (local PostgreSQL, debug logging for Spring Security)
- `dev` — shared dev environment
- `stg` — staging environment
- `prd` — production (deployed by your infrastructure from a published GitHub Release)

## Architecture

This is a standalone Spring Boot 3.x (Java 21) self-hosted OIDC Authorization Server. It acts as the single identity provider for all connected applications (web apps, self-hosted tools, APIs).

Runs on **port 9000**. Uses PostgreSQL for all persistent state.

See `RUNBOOK.md` for full local setup and AWS deploy instructions.

### Key design points

- Spring Authorization Server issues ID tokens, access tokens, and refresh tokens to downstream client apps
- Supports two user types: `LOCAL` (BCrypt + TOTP MFA) and `FEDERATED` (upstream OIDC IDPs — Google, Okta, Azure AD)
- Home Realm Discovery (HRD): email-first login determines auth path
- All IDP configs and client registrations are stored in PostgreSQL — no restart needed to add new apps or IDPs
- Flyway manages all schema migrations (V1–V13)
- `ClientAccessFilter` gates which users can log in to which client applications (per-user access control in `user_client_access` table)

### Request flow

| Request | Handler | Notes |
|---|---|---|
| `GET /login` | `LoginController` → `login.html` | Email-first HRD form |
| `GET /hrd/lookup?email=...` | `HrdController` | Returns `{ authType, registrationId }` |
| `POST /login` | Spring Security `UsernamePasswordAuthenticationFilter` | Form login for LOCAL users |
| `GET /oauth2/authorize` | Spring Authorization Server | Authorization endpoint |
| `POST /oauth2/token` | Spring Authorization Server | Token exchange |
| `GET /oauth2/jwks` | Spring Authorization Server | Public key set |
| `GET /.well-known/openid-configuration` | Spring Authorization Server | OIDC discovery |
| `GET /connect/logout` | Spring Authorization Server `OidcLogoutEndpointFilter` | RP-Initiated Logout; custom `logoutResponseHandler` calls `SecurityContextLogoutHandler` unconditionally then redirects to `post_logout_redirect_uri` |
| `GET /mfa/challenge` | `MfaController` | TOTP challenge form (requires `PRE_MFA` authority) |
| `POST /mfa/challenge` | `MfaController` | Verifies TOTP or recovery code; promotes to full session |
| `GET /mfa/enroll` | `MfaController` | TOTP enrollment (generates QR code) |
| `POST /mfa/enroll/confirm` | `MfaController` | Confirms enrollment; generates recovery codes |
| `GET /mfa/recovery-codes` | `MfaController` | Shows recovery codes once after enrollment |
| `GET /account` | `AccountController` | User self-service: profile + MFA status + recovery code count |
| `POST /account/mfa/disable` | `AccountController` | Removes MFA enrollment (blocked if `mfa_required=true`) |
| `GET /admin/**` | `AdminController` | User/IDP/client management (ADMIN role required) |
| `GET /actuator/health` | Spring Boot Actuator | Public health check |

### Filter chain order

| Order | Chain | Handles |
|---|---|---|
| 1 | `authorizationServerSecurityFilterChain` | OAuth2/OIDC protocol endpoints (`/oauth2/**`, `/connect/**`, `/.well-known/**`) |
| 2 | `filterChain` | All other requests (form login, MFA, admin, account) |

`MfaEnrollmentFilter` sits after `AnonymousAuthenticationFilter` in Order 2. It redirects users with `mfa_required=true` and `mfa_enabled=false` to `/mfa/enroll`.

`ClientAccessFilter` sits after `SecurityContextHolderFilter` in Order 1. It rejects authorization requests if the authenticated user has no row in `user_client_access` for the requested `client_id`.

`RateLimitFilter` applies Bucket4j rate limiting to `/login` and `/hrd/lookup`.

### MFA flow

```
POST /login (BCrypt OK)
  → MfaAuthenticationSuccessHandler
      mfa_enabled=true  → store full Authentication in session as PENDING_MFA_AUTH
                          grant PRE_MFA authority → 302 /mfa/challenge
      mfa_enabled=false → SavedRequest-aware redirect (OAuth2 authorize URL or /)
```

After MFA success, `MfaController` promotes the pending auth to a full `SecurityContext` and replays the saved OAuth2 authorization request.

### Key configuration properties

| Property | Where |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | env vars / application-prd.properties |
| `AUTH_SERVER_BASE_URL` | env var — public URL used in issuer claim e.g. `https://auth.example.com` |
| `AUTH_SERVER_SIGNING_KEY_REF` | AWS Secrets Manager ARN for RSA-2048 signing key (planned; currently using generated ephemeral key) |
| `MFA_ENCRYPTION_KEY_REF` | AWS Secrets Manager ARN for AES-GCM key (planned; currently storing raw Base32 secret) |
| `STOREFRONT_BASE_URL` | Base URL of the storefront app — used in OIDC logout redirect validation |
