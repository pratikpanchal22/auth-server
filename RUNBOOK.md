# Auth Server — Runbook

This document covers everything needed to build, run, test, and deploy the auth server from scratch. It is updated with each PR as new capabilities are added.

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
| Maven | 3.9+ | `brew install maven` |
| Docker Desktop | Latest | https://www.docker.com/products/docker-desktop |
| AWS CLI | v2 | `brew install awscli` (prod only) |

Verify your setup:

```bash
java -version      # openjdk 21.x.x
mvn -version       # Apache Maven 3.9.x
docker info        # must show server info — Docker Desktop must be running
```

---

## Project Structure

```
auth-server/                        ← repo root (this project)
  src/
    main/
      java/io/github/pratikpanchal22/authserver/
        AuthServerApplication.java
      resources/
        application.properties            ← common config (port 9000, actuator)
        application-local.properties      ← local dev (Docker PostgreSQL)
        application-dev.properties        ← shared dev environment
        application-prod.properties       ← production (all values from env vars)
    test/
      java/io/github/pratikpanchal22/authserver/
        AuthServerApplicationTests.java   ← context loads test
        HealthEndpointTest.java           ← /actuator/health tests
      resources/
        application-test.properties       ← test overrides (Flyway disabled)
  compose.yaml      ← PostgreSQL 15 + auth-server for local dev
  Dockerfile        ← multi-stage build (Maven → JRE image)
  .env.example      ← template for local secrets (copy to .env)
  pom.xml           ← standalone Maven project (Java 21, Spring Boot 3.5.7)
  RUNBOOK.md        ← this file
```

This is a **standalone Maven project**. Run all `mvn` commands from this directory.

---

## Environment Variables

Copy `.env.example` to `.env` before running via Docker Compose. Never commit `.env`.

```bash
cp .env.example .env
```

| Variable | Required in | Default (local) | Description |
|---|---|---|---|
| `DB_PASSWORD` | All | `localpassword` | PostgreSQL password |
| `DB_URL` | Prod | — | Full JDBC URL e.g. `jdbc:postgresql://host:5432/auth_db` |
| `DB_USER` | Prod | — | PostgreSQL username |
| `AUTH_SERVER_BASE_URL` | Prod | — | Public URL e.g. `https://auth.yourdomain.com` — used in OIDC issuer claim |
| `AUTH_SERVER_SIGNING_KEY_REF` | Prod | — | AWS Secrets Manager ARN for RSA-2048 private key |
| `MFA_ENCRYPTION_KEY_REF` | Prod | — | AWS Secrets Manager ARN for AES-GCM encryption key |

> Variables marked "Prod" are not needed for local development and are wired in later PRs.

---

## Running Locally

The auth server runs on **port 9000**. PostgreSQL runs on 5432 (Docker).

### Option A — Native app + Docker PostgreSQL (recommended for development)

This is the fastest dev loop. PostgreSQL runs in Docker; the app runs natively with Spring Boot DevTools hot reload.

**Step 1 — Start Docker Desktop**

```bash
open -a Docker
# Wait ~30 seconds, then verify:
docker info
```

**Step 2 — Start PostgreSQL**

```bash
docker compose up postgresql -d

# Verify it's healthy:
docker compose ps
```

**Step 3 — Run the app**

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

You should see:

```
Started AuthServerApplication in X.XXX seconds
Tomcat started on port 9000
```

**Step 4 — Verify**

```bash
curl http://localhost:9000/actuator/health
# Expected: {"status":"UP"}

curl http://localhost:9000/actuator/info
# Expected: {"app":{"name":"Auth Server","version":"0.0.1-SNAPSHOT"}}
```

**Stopping**

```bash
# Ctrl+C to stop the app

docker compose down              # stop PostgreSQL, keep data volume
docker compose down -v           # stop PostgreSQL, delete volume (fresh DB)
```

---

### Option B — Fully containerised

```bash
cp .env.example .env             # only needed once
docker compose up --build
```

Run in background:

```bash
docker compose up --build -d
docker compose logs -f auth-server
```

Verify:

```bash
curl http://localhost:9000/actuator/health
```

---

## Running Tests

Tests require no database — Flyway is disabled in the test profile.

```bash
mvn test
```

Expected output:

```
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### What the tests cover (PR-01)

| Test | Class | Verifies |
|---|---|---|
| `contextLoads` | `AuthServerApplicationTests` | Spring context starts without errors |
| `healthEndpointReturns200` | `HealthEndpointTest` | `GET /actuator/health` returns HTTP 200 |
| `healthResponseContainsStatusUp` | `HealthEndpointTest` | Response body contains `"UP"` |

Run a single test class:

```bash
mvn test -Dtest=HealthEndpointTest
```

---

## Docker

### Multi-stage Dockerfile

The `Dockerfile` uses a two-stage build:

1. **Build stage** (`maven:3.9-eclipse-temurin-21-alpine`) — compiles inside Docker, no local Java/Maven required
2. **Runtime stage** (`eclipse-temurin:21-jre-alpine`) — minimal JRE image (~200 MB)

`docker compose up --build` works on any machine with Docker installed.

### Build and run the image manually

```bash
mvn clean package -DskipTests
docker build -t auth-server .
docker run -p 9000:9000 -e SPRING_PROFILES_ACTIVE=local auth-server
```

---

## AWS Deploy

### First-time EC2 Setup

**1 — Install Docker on Amazon Linux 2023**

```bash
sudo dnf install -y docker
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user
# Log out and back in for group change to take effect
```

**2 — Install Docker Compose**

```bash
sudo curl -L \
  "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64" \
  -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose
```

**3 — Clone the repo**

```bash
git clone https://github.com/pratikpanchal22/auth-server.git /opt/auth-server
cd /opt/auth-server
```

**4 — Set up environment file**

```bash
cp .env.example .env
nano .env    # fill in DB_PASSWORD and production values
```

**5 — Start**

```bash
docker compose up --build -d
docker compose logs -f auth-server
```

---

### Deploy an Update

```bash
cd /opt/auth-server
git pull origin main
docker compose up --build -d
docker compose logs -f auth-server
curl http://localhost:9000/actuator/health
```

> Flyway migrations (added in PR-02) run automatically on startup.

### ALB Subdomain Routing

| Subdomain | EC2 Port |
|---|---|
| `auth.yourdomain.com` | 9000 |

TLS termination at the ALB; the app runs HTTP internally.

---

## Verification Checklist

- [ ] `curl http://localhost:9000/actuator/health` → `{"status":"UP"}`
- [ ] `curl http://localhost:9000/actuator/info` → returns app name and version
- [ ] `docker compose ps` → all containers show `healthy` or `running`
- [ ] No `ERROR` lines in `docker compose logs auth-server`

---

## Troubleshooting

### Docker daemon not running

```
Cannot connect to the Docker daemon at unix:///.../.docker/run/docker.sock
```

Start Docker Desktop: `open -a Docker`, wait ~30 seconds, retry.

---

### Port 9000 already in use

```bash
lsof -ti:9000 | xargs kill -9
```

---

### Port 5432 already in use

```bash
lsof -ti:5432          # find what's using it
docker compose down    # or stop existing containers
```

---

### PostgreSQL container not healthy

```bash
docker compose logs postgresql
# Stale volume with old credentials? Run:
docker compose down -v && docker compose up postgresql -d
```

---

### Wrong Java version

```bash
java -version    # confirm 21
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
# Add to ~/.zshrc to persist
```

---

### Build fails: package not found

Make sure you're running `mvn` from the repo root (same directory as `pom.xml`):

```bash
pwd   # should end in /auth-server
mvn spring-boot:run -Dspring-boot.run.profiles=local
```
