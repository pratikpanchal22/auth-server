# Phase-10 — Calibre-Web + Draw.io Client Onboarding

## What changed

- `ClientDataLoader` now seeds **three** clients on startup (non-prod): `storefront`, `calibre-web`, `drawio`
- Each client is checked independently (no early-return on storefront present)
- `RegisteredClientRepositoryTest` extended with assertions for both new clients

---

## Repo layout

Each client app lives in its own top-level directory, mirroring the storefront pattern:

```
nthNode/
  auth-server/      ← this repo
  store-front/
  calibre-web/      ← fork of janeczku/calibre-web
  drawio/           ← docker-compose.yml only (no source changes needed)
```

---

## Client registrations

| Client ID | Secret (dev) | Redirect URI(s) | Scopes |
|---|---|---|---|
| `calibre-web` | `calibre-web-secret` | `http://localhost:8083/login`, `https://books.nthnode.com/login` | openid, profile, email |
| `drawio` | `drawio-secret` | `http://localhost:8888/`, `https://draw.nthnode.com/` | openid, profile, email |

Both use `client_secret_basic` auth, `authorization_code + refresh_token` grants, and refresh token rotation (reuseRefreshTokens=false).

> **Note on redirect URIs**: the exact callback path each app sends must be verified on first test run. Spring AS logs `redirect_uri_mismatch` if the stored URI doesn't match. Add the correct URI to `ClientDataLoader` (dev) or the `oauth2_registered_client` row directly (prod).

---

## Local testing setup

Each service starts independently. Start the auth server first.

### Auth server

```bash
cd auth-server
docker compose up    # postgresql + auth-server on port 9000
```

### Draw.io

```bash
cd drawio
docker compose up    # draw.io on port 8888
```

Navigate to `http://localhost:8888` — Draw.io redirects to the Auth Server login immediately.

`host.docker.internal` in the compose file lets the container reach the Auth Server on port 9000 from inside Docker.

> If Draw.io sends a different redirect URI than `http://localhost:8888/` (visible in Auth Server logs as `redirect_uri_mismatch`), add the correct URI to `ClientDataLoader.seedDrawio()`.

### Calibre-Web

Calibre-Web is a fork of `janeczku/calibre-web`. Build and run from that repo's directory. A `docker-compose.yml` will live there.

On first visit to `http://localhost:8083`, point it at a books directory.

**Configure OIDC** (one-time, via admin UI):
1. **Admin → Edit Basic Configuration → Feature Configuration** → enable **Allow OAuth** → Save
2. **Admin → OAuth Providers** → add provider:
   - **Provider name**: `nthnode`
   - **Client ID**: `calibre-web`
   - **Client Secret**: `calibre-web-secret`
   - **Authorization URL**: `http://localhost:9000/oauth2/authorize`
   - **Token URL**: `http://localhost:9000/oauth2/token`
   - **Userinfo URL**: `http://localhost:9000/userinfo`
3. Save — note the **Redirect URI** shown. Update `ClientDataLoader.seedCalibreWeb()` if it differs from `http://localhost:8083/login`.

**Verify SSO**: log out → click **nthnode** login button → redirects to Auth Server → sign in → lands back in Calibre-Web authenticated.

---

## Production deployment

Client secrets must be rotated for production. Insert client rows via the admin UI (Phase-11) or a migration script — do **not** use the dev secrets.

### Draw.io (docker-compose on EC2)

```yaml
DRAWIO_OIDC_ISSUER: https://auth.nthnode.com
DRAWIO_OIDC_CLIENT_ID: drawio
DRAWIO_OIDC_CLIENT_SECRET: ${DRAWIO_OIDC_SECRET}   # inject from EC2 env / Secrets Manager
```

### Calibre-Web (EC2)

Configure via admin UI with production values:
- Auth URL: `https://auth.nthnode.com/oauth2/authorize`
- Token URL: `https://auth.nthnode.com/oauth2/token`
- Userinfo URL: `https://auth.nthnode.com/userinfo`
- Client Secret: (from AWS Secrets Manager)

---

## Manual verification checklist

- [ ] Auth Server health: `curl http://localhost:9000/actuator/health` → `{"status":"UP"}`
- [ ] `calibre-web` and `drawio` clients in DB: `SELECT client_id FROM oauth2_registered_client;`
- [ ] Draw.io: `http://localhost:8888` redirects to Auth Server login
- [ ] Draw.io: after sign-in, editor loads
- [ ] Calibre-Web: OAuth login button redirects to Auth Server
- [ ] Calibre-Web: after sign-in, returns as authenticated user
- [ ] Signing out of one app does not affect the other (no shared session between clients)
