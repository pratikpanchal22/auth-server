# Auth Server — Runbook

This document covers everything needed to build, run, test, and deploy the `auth-server` from scratch. It is updated with each PR as new capabilities are added.

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
store-front/                        ← repo root
  auth-server/                      ← this project (standalone Spring Boot app)
    src/
      main/
        java/com/nthnode/authserver/
          AuthServerApplication.java
        resources/
          application.properties            ← common config (port 9000, actuator)
          application-local.properties      ← local dev (Docker PostgreSQL)
          application-dev.properties        ← shared dev environment
          application-prod.properties       ← production (all values from env vars)
      test/
        java/com/nthnode/authserver/
          AuthServerApplicationTests.java   ← context loads test
          HealthEndpointTest.java           ← /actuator/health tests
        resources/
          application-test.properties       ← test overrides (Flyway disabled)
    compose.yaml                    ← auth-server + PostgreSQL (for auth-server dev)
    Dockerfile                      ← multi-stage build (Maven → JRE image)
    .env.example                    ← template for local secrets (copy to .env)
    pom.xml                         ← standalone Maven project (Java 21, SB 3.5.7)
    RUNBOOK.md                      ← this file
  compose.yaml                      ← root: storefront + auth-server + PostgreSQL together
  pom.xml                           ← storefront Maven project (independent)
```

The auth-server is a **separate Maven project** from the storefront. Each is built and run independently from its own directory. They share this git repo but have no compile-time dependency on each other.

---

## Environment Variables

Copy `.env.example` to `.env` before running via Docker Compose. Never commit `.env`.

```bash
cp auth-server/.env.example auth-server/.env
```

| Variable | Required in | Default (local) | Description |
|---|---|---|---|
| `DB_PASSWORD` | All | `localpassword` | PostgreSQL password |
| `DB_URL` | Prod | — | Full JDBC URL e.g. `jdbc:postgresql://host:5432/nthnode_auth_db` |
| `DB_USER` | Prod | — | PostgreSQL username |
| `AUTH_SERVER_BASE_URL` | Prod | — | Public URL e.g. `https://auth.nthnode.com` — used in OIDC issuer claim |
| `AUTH_SERVER_SIGNING_KEY_REF` | Prod | — | AWS Secrets Manager ARN for RSA-2048 private key |
| `MFA_ENCRYPTION_KEY_REF` | Prod | — | AWS Secrets Manager ARN for AES-GCM encryption key |

> Variables marked "Prod" are not needed for local development and are added to `application-prod.properties` in later PRs.

---

## Running Locally

The auth-server runs on **port 9000**. The storefront runs on port 8080. PostgreSQL runs on 5432 (Docker, internal only in prod).

### Option A — Native app + Docker PostgreSQL (recommended for development)

This is the fastest dev loop. PostgreSQL runs in Docker; the app runs natively on your machine with Spring Boot DevTools hot reload.

**Step 1 — Start Docker Desktop**

Make sure Docker Desktop is running before proceeding:

```bash
open -a Docker
# Wait ~30 seconds, then verify:
docker info
```

**Step 2 — Start PostgreSQL**

```bash
cd auth-server
docker compose up postgresql -d
```

Verify PostgreSQL is healthy:

```bash
docker compose ps
# postgresql should show "healthy"
```

**Step 3 — Run the auth-server**

```bash
# From auth-server/ directory
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

You should see Spring Boot start on port 9000:

```
Started AuthServerApplication in X.XXX seconds
Tomcat started on port 9000
```

**Step 4 — Verify**

```bash
curl http://localhost:9000/actuator/health
# Expected: {"status":"UP"}

curl http://localhost:9000/actuator/info
# Expected: {"app":{"name":"nthNode Auth Server","version":"0.0.1-SNAPSHOT"}}
```

**Stopping**

```bash
# Stop the app: Ctrl+C in the terminal running mvn spring-boot:run

# Stop PostgreSQL
docker compose down
```

---

### Option B — Fully containerised (closest to AWS)

Both the app and PostgreSQL run as Docker containers. Requires a Docker build on every code change.

```bash
cd auth-server
cp .env.example .env          # only needed once

docker compose up --build
```

The `--build` flag rebuilds the app image. Use this after any code changes. Logs stream to the terminal.

To run in the background:

```bash
docker compose up --build -d
docker compose logs -f auth-server    # follow logs
```

Verify:

```bash
curl http://localhost:9000/actuator/health
# Expected: {"status":"UP"}
```

Stop and clean up:

```bash
docker compose down           # stops containers, keeps postgres_data volume
docker compose down -v        # stops containers AND deletes the volume (fresh DB)
```

---

### Running Both Apps Together (storefront + auth-server)

From the **repo root** (not `auth-server/`):

```bash
cp auth-server/.env.example .env    # only needed once

docker compose up --build
```

This starts:
- `storefront` → http://localhost:8080
- `auth-server` → http://localhost:9000
- `postgresql` → port 5432 (internal to Docker network only)

> Note: The storefront will not have a working login flow until PR-06 (storefront migration). At this stage `docker compose up` from the root is mainly for integration verification.

---

## Running Tests

Tests require no database — Flyway is disabled in the test profile and there are no JPA dependencies in this PR.

```bash
cd auth-server
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
| `contextLoads` | `AuthServerApplicationTests` | Spring application context starts without errors |
| `healthEndpointReturns200` | `HealthEndpointTest` | `GET /actuator/health` returns HTTP 200 |
| `healthResponseContainsStatusUp` | `HealthEndpointTest` | Response body contains `"UP"` |

Run a single test class:

```bash
mvn test -Dtest=HealthEndpointTest
```

---

## Docker

### Build the image manually

```bash
cd auth-server
mvn clean package -DskipTests        # build the JAR first
docker build -t nthnode-auth-server .
```

### Run the image directly

```bash
docker run -p 9000:9000 \
  -e SPRING_PROFILES_ACTIVE=local \
  nthnode-auth-server
```

### Multi-stage Dockerfile explained

The `Dockerfile` uses a two-stage build:

1. **Build stage** (`maven:3.9-eclipse-temurin-21-alpine`) — downloads dependencies and compiles the JAR inside Docker. No local Maven or Java install required.
2. **Runtime stage** (`eclipse-temurin:21-jre-alpine`) — copies only the compiled JAR into a minimal JRE image (~200 MB vs ~500 MB for JDK).

This means `docker compose up --build` works on any machine with Docker, even without Java or Maven installed.

---

## AWS Deploy

### First-time EC2 Setup

These steps are performed once when provisioning a new EC2 instance.

**1 — Install Docker on Amazon Linux 2023**

```bash
sudo dnf install -y docker
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user
# Log out and back in for the group change to take effect
```

**2 — Install Docker Compose**

```bash
sudo curl -L \
  "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64" \
  -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose
docker-compose --version
```

**3 — Clone the repo**

```bash
git clone https://github.com/nthNode/store-front.git /opt/nthnode
```

**4 — Set up the environment file**

```bash
cd /opt/nthnode
cp auth-server/.env.example auth-server/.env
nano auth-server/.env    # fill in DB_PASSWORD and any prod values
```

**5 — Start the auth-server**

```bash
cd /opt/nthnode/auth-server
docker compose up --build -d
docker compose logs -f auth-server
```

---

### Deploy an Update

Run these steps every time a new version is merged to `main`:

```bash
cd /opt/nthnode

# Pull latest code
git pull origin main

# Rebuild and restart auth-server
cd auth-server
docker compose up --build -d

# Follow logs to confirm clean startup
docker compose logs -f auth-server

# Verify
curl http://localhost:9000/actuator/health
```

> Flyway migrations (added in PR-02) run automatically on startup. If a migration fails, the app will refuse to start and log the error — check `docker compose logs auth-server`.

### ALB Subdomain Routing (AWS)

Once Route 53 and an Application Load Balancer are configured:

| Subdomain | ALB Target | EC2 Port |
|---|---|---|
| `auth.nthnode.com` | auth-server target group | 9000 |
| `storefront.nthnode.com` | storefront target group | 8080 |

TLS termination happens at the ALB. The app itself runs HTTP internally.

---

## Verification Checklist

After every local start or AWS deploy, verify:

- [ ] `curl http://localhost:9000/actuator/health` → `{"status":"UP"}`
- [ ] `curl http://localhost:9000/actuator/info` → returns app name and version
- [ ] `docker compose ps` → all containers show `healthy` or `running`
- [ ] No `ERROR` lines in `docker compose logs auth-server`

---

## Troubleshooting

### Docker daemon not running

```
Cannot connect to the Docker daemon at unix:///Users/.../.docker/run/docker.sock
```

Start Docker Desktop: `open -a Docker`, wait ~30 seconds, then retry.

---

### Port 9000 already in use

```
Web server failed to start. Port 9000 was already in use.
```

Find and kill the process using the port:

```bash
lsof -ti:9000 | xargs kill -9
```

---

### Port 5432 already in use

Another PostgreSQL instance (local or Docker) is already on 5432.

```bash
# Find what's using it
lsof -ti:5432

# Or stop all Docker containers
docker compose down
```

---

### PostgreSQL container not healthy

```bash
docker compose logs postgresql
```

Common cause: a stale volume from a previous run with different credentials.

```bash
docker compose down -v    # deletes the volume — fresh start
docker compose up postgresql -d
```

---

### `mvn spring-boot:run` can't find Java 21

```bash
java -version    # confirm 21 is active
```

If you have multiple JDKs, set `JAVA_HOME`:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

Add to `~/.zshrc` to make it permanent.

---

### Build fails: `package com.nthnode.authserver does not exist`

You're running `mvn` from the wrong directory. Always run from `auth-server/`:

```bash
cd /path/to/store-front/auth-server
mvn spring-boot:run -Dspring-boot.run.profiles=local
```
