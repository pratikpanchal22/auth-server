# Phase-10 — Calibre-Web + Draw.io Client Onboarding

## What changed

- `ClientDataLoader` now seeds **three** clients on startup (non-prod): `storefront`, `calibre-web`, `drawio`
- Each client is checked independently (no early-return on storefront present)
- `compose.yaml` gains `calibre-web` and `drawio` services for local testing
- `RegisteredClientRepositoryTest` extended with assertions for both new clients

---

## Client registrations

| Client ID | Secret (dev) | Redirect URI(s) | Scopes |
|---|---|---|---|
| `calibre-web` | `calibre-web-secret` | `http://localhost:8083/login`, `https://books.nthnode.com/login` | openid, profile, email |
| `drawio` | `drawio-secret` | `http://localhost:8888/`, `https://draw.nthnode.com/` | openid, profile, email |

Both use `client_secret_basic` auth, `authorization_code + refresh_token` grants, and refresh token rotation (reuseRefreshTokens=false).

> **Note on redirect URIs**: the exact callback path each app sends during the OAuth2 flow must be verified on first test run. Spring AS will return `redirect_uri_mismatch` if the stored URI doesn't match exactly. Add additional URIs to `ClientDataLoader` (dev) or update the `oauth2_registered_client` row directly (prod) if needed.

---

## Local testing setup

### Start the full stack

```bash
# From auth-server repo root
docker compose up
```

This starts:
- `postgresql` (port 5432)
- `auth-server` (port 9000)
- `calibre-web` (port 8083)
- `drawio` (port 8888)

### Calibre-Web first-run setup

Calibre-Web requires a Calibre library on first startup. Create a placeholder books directory:

```bash
mkdir -p books
```

On first visit to `http://localhost:8083`, Calibre-Web will ask you to configure a books location. Point it to `/books`.

**Default admin credentials:** `admin` / `admin123` (change immediately)

### Configure Calibre-Web OIDC

1. Log in to Calibre-Web with the local admin account
2. Go to **Admin → Edit Basic Configuration → Feature Configuration**
3. Enable **Allow OAuth**
4. Go to **Admin → OAuth Providers** → add a new provider:
   - **Provider name**: `nthnode`
   - **Client ID**: `calibre-web`
   - **Client Secret**: `calibre-web-secret`
   - **Authorization URL**: `http://localhost:9000/oauth2/authorize`
   - **Token URL**: `http://localhost:9000/oauth2/token`
   - **Userinfo URL**: `http://localhost:9000/userinfo`
5. Save and note the **callback URL** Calibre-Web displays — update `ClientDataLoader.seedCalibreWeb()` if it differs from `http://localhost:8083/login`

### Verify Calibre-Web SSO

1. Log out of Calibre-Web
2. Click the provider login button on the login page
3. Browser redirects to Auth Server login
4. Sign in with your nthNode account
5. Should return to Calibre-Web authenticated

### Draw.io OIDC

Draw.io self-hosted receives OIDC config via environment variables in `compose.yaml`:

```yaml
DRAWIO_OIDC_ENABLED=1
DRAWIO_OIDC_ISSUER=http://host.docker.internal:9000
DRAWIO_OIDC_CLIENT_ID=drawio
DRAWIO_OIDC_CLIENT_SECRET=drawio-secret
```

`host.docker.internal` resolves to the host machine from inside Docker, allowing the container to reach the Auth Server on port 9000.

Navigate to `http://localhost:8888` — if OIDC is configured correctly, Draw.io will redirect to the Auth Server login before showing the editor.

> **Draw.io callback URI**: the redirect URI Draw.io sends is `http://localhost:8888/`. If Draw.io sends a different URI (check the `redirect_uri_mismatch` error in the Auth Server logs), add the correct URI to `ClientDataLoader.seedDrawio()`.

---

## Production deployment

For production, client secrets should be rotated. The `storefront`, `calibre-web`, and `drawio` rows in `oauth2_registered_client` on the production DB can be inserted directly via the admin UI (Phase-11) or a migration script. Do **not** use the dev secrets.

**Environment variables for each app (production):**

### Calibre-Web (EC2)
Configure via admin UI with production values:
- Auth URL: `https://auth.nthnode.com/oauth2/authorize`
- Token URL: `https://auth.nthnode.com/oauth2/token`
- Userinfo URL: `https://auth.nthnode.com/userinfo`
- Client ID: `calibre-web`
- Client Secret: (from AWS Secrets Manager)

### Draw.io (docker-compose on EC2)
```yaml
DRAWIO_OIDC_ISSUER: https://auth.nthnode.com
DRAWIO_OIDC_CLIENT_ID: drawio
DRAWIO_OIDC_CLIENT_SECRET: ${DRAWIO_OIDC_SECRET}   # inject from EC2 env
```

---

## Manual verification checklist

- [ ] `docker compose up` starts all four services without errors
- [ ] Auth Server health: `curl http://localhost:9000/actuator/health` → `{"status":"UP"}`
- [ ] `calibre-web` and `drawio` clients appear in DB: `SELECT client_id FROM oauth2_registered_client;`
- [ ] Calibre-Web: OAuth login redirects to Auth Server login page
- [ ] Calibre-Web: after sign-in, returns to Calibre-Web as authenticated user
- [ ] Draw.io: navigating to `http://localhost:8888` redirects to Auth Server
- [ ] Draw.io: after sign-in, Draw.io editor loads
- [ ] Signing out of one app does not affect the other (no shared session between clients)
