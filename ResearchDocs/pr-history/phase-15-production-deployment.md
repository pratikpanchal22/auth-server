# Phase-15: Production Deployment Infrastructure

## What this phase does

Establishes the production deployment pipeline: GitHub Actions builds the JAR, uploads to S3, and deploys to EC2 via SSM Run Command. The app runs as a systemd service behind Nginx on a single EC2 instance.

---

## What changed

| Before | After |
|--------|-------|
| Local-only — no production deploy | GitHub Actions CI/CD on push to `main` |
| No production config | `application-prd.properties` with all sensitive values from env vars |
| Docker-based local dev only | JAR + systemd in production; Docker only for local dev |
| Hardcoded EC2 instance ID in SSM command | SSM tag-based targeting (`Name=nthnode-app`) |

---

## Deployment architecture

```
GitHub Actions (push → main)
  → mvnw clean package -DskipTests
  → aws s3 cp target/*.jar s3://nthnode-backups/deployments/auth-server/app.jar
  → aws ssm send-command (tag Name=nthnode-app)
      → s3 cp → /opt/auth-server/app.jar
      → systemctl restart auth-server
      → systemctl is-active auth-server

EC2 (Amazon Linux 2023, single instance)
  Nginx (443 TLS) ──► auth-server (9000 HTTP)
  systemd: /etc/systemd/system/auth-server.service
```

---

## `application-prd.properties` highlights

```properties
server.port=9000
server.forward-headers-strategy=framework   # trust Nginx X-Forwarded-* headers
spring.flyway.enabled=true
spring.jpa.hibernate.ddl-auto=validate
auth.server.base-url=${AUTH_SERVER_BASE_URL}
storefront.base-url=https://nthnode.us
```

All DB credentials and secrets come from environment variables set in the systemd unit file.

---

## Post-Phase-15 operational fixes (not separate phases)

| PR | What it fixed |
|----|---------------|
| #37 | `server.forward-headers-strategy=framework` — OAuth2 redirect URIs were `http://` behind Nginx |
| #39 | IDP avatar and display name in navbar (federated users showed raw account ID) |
| #41 | Shared nav fragment across auth-server pages; logo links to storefront |
| #43 | Replace hardcoded EC2 instance ID with SSM tag-based targeting |
| #44/#45 | Standardize profile names: `local` → `local-dev`, `prod` → `prd` across all properties files |
| #46 | Remove stale `sed` profile migration from deploy workflow |
| #47/#48 | Fix post-login redirect — `SavedRequestAwareAuthenticationSuccessHandler` honours the pending `/oauth2/authorize` URL for local users |
| #49/#50 | Fix post-MFA redirect — MFA challenge now replays the saved OAuth2 authorization request, not just `/` |
| #54 | Fix RP-Initiated Logout: custom `logoutResponseHandler` in `AuthorizationServerConfig` calls `SecurityContextLogoutHandler` unconditionally; also fixed `defaultSuccessUrl("/", false)` for federated users |
