# CLAUDE.md

This file provides guidance to Claude Code when working with code in this repository.

## Commands

```bash
# Run locally (PostgreSQL must be running — see RUNBOOK.md)
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Run fully containerised
docker compose up --build

# Run tests (no database required)
mvn test

# Build JAR
mvn clean package -DskipTests

# Start PostgreSQL only (for native dev)
docker compose up postgresql -d
```

## Architecture

This is a standalone Spring Boot 3.x (Java 21) OIDC Authorization Server for nthNode, LLC. It is the single identity provider for all nthNode applications (storefront, Calibre-Web, Draw.io, etc.).

Runs on **port 9000**. Uses PostgreSQL for all persistent state.

See `RUNBOOK.md` for full local setup, Docker, and AWS deploy instructions.

### Key design points

- Spring Authorization Server issues ID tokens, access tokens, and refresh tokens to downstream client apps
- Supports two user types: `LOCAL` (BCrypt + TOTP MFA) and `FEDERATED` (OIDC upstream IDPs — Google, Okta, Azure AD)
- Home Realm Discovery (HRD): email-first login determines auth path
- All IDP configs and client registrations are stored in PostgreSQL — no restart needed to add new apps or IDPs
- Flyway manages all schema migrations

### Request flow (once fully implemented)

- `GET /login` → email-first HRD form
- `POST /auth/lookup` → returns authType (LOCAL/FEDERATED) for a given email
- `POST /auth/login` → Spring Security form login for LOCAL users
- `POST /auth/mfa` → TOTP challenge for MFA-enabled LOCAL users
- `GET /oauth2/authorize` → Spring Authorization Server authorization endpoint
- `POST /oauth2/token` → token exchange endpoint
- `GET /oauth2/jwks` → public key set for token validation
- `GET /.well-known/openid-configuration` → OIDC discovery document
- `GET /admin/**` → admin UI (ADMIN role required)

### Key configuration properties

| Property | Where |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | env vars / application-prod.properties |
| `AUTH_SERVER_BASE_URL` | env var — public URL used in issuer claim |
| `AUTH_SERVER_SIGNING_KEY_REF` | AWS Secrets Manager ARN for RSA-2048 signing key |
| `MFA_ENCRYPTION_KEY_REF` | AWS Secrets Manager ARN for AES-GCM key |

### Design document

Full architecture, auth flows, data model, and implementation plan:
`store-front` repo → `docs/nthnode-auth-platform-design.md`
