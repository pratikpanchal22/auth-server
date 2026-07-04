# PR-01: Project Scaffold

## What this PR does

Bootstraps the `auth-server` Spring Boot 3.x application — the empty shell that all subsequent PRs build on. No business logic is introduced here; the goal is to establish the build, runtime profiles, Docker packaging, and health endpoints so the rest of the system can be layered on top.

---

## Added

| Artifact | Purpose |
|----------|---------|
| `pom.xml` | Spring Boot 3.5.x parent, Java 21, `spring-boot-starter-web`, `spring-boot-starter-thymeleaf`, `spring-boot-starter-actuator` |
| `AuthServerApplication.java` | Entry point |
| `application.properties` | Shared config: port `9000`, Actuator endpoints, Thymeleaf settings |
| `application-local.properties` | Local dev profile — PostgreSQL via Docker Compose |
| `application-prod.properties` | Production profile — all sensitive values from environment variables |
| `Dockerfile` | Multi-stage build (Maven build → slim JRE runtime image) |
| `compose.yaml` | `postgresql` service (with health-check) + `auth-server` service |

---

## Runtime Profiles

| Profile | Database | Intended environment |
|---------|----------|----------------------|
| `local` | PostgreSQL on `localhost:5432` via Docker Compose | Developer laptop |
| `prod` | PostgreSQL via `DB_URL` / `DB_USER` / `DB_PASSWORD` env vars | EC2 / ECS |
| `test` | H2 in-memory | Unit and integration tests (CI) |

### Why three profiles?

The existing Flyway migrations (added in PR-02) use PostgreSQL-specific syntax (`gen_random_uuid()`, `TIMESTAMP WITH TIME ZONE`, `JSONB`). Running them against H2 would require workarounds that obscure real-world behaviour. The `test` profile intentionally disables Flyway; tests that need a real schema use Testcontainers with a live PostgreSQL container instead.

---

## Application layout

```
auth-server/
├── src/
│   ├── main/
│   │   ├── java/io/github/pratikpanchal22/authserver/
│   │   │   └── AuthServerApplication.java
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── application-local.properties
│   │       └── application-prod.properties
│   └── test/
│       └── resources/
│           └── application-test.properties   (added PR-02)
├── Dockerfile
└── compose.yaml
```

---

## Local quick-start

```bash
# Start PostgreSQL
docker compose up postgresql -d

# Run the app (local profile)
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Health check: `http://localhost:9000/actuator/health`
