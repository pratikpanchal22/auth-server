# Phase-12: Audit Logging + Rate Limiting

## What this phase does

Adds a persistent audit trail for security-relevant events and per-IP rate limiting on the login and token endpoints using Bucket4j.

---

## What changed

| Before | After |
|--------|-------|
| No audit trail | `audit_events` table; `AuditService` logs key events |
| No rate limiting | `RateLimitFilter` — 10 login attempts / 10 min; 20 token requests / min per IP |
| No audit admin view | `/admin/audit` — last 200 events in reverse-chron table |
| Login failure URL hardcoded | `LoginFailureHandler` component — records failure + logs `LOGIN_FAILURE` |
| Logout URL hardcoded | `AuditLogoutSuccessHandler` component — logs `LOGOUT` before redirecting |

---

## Audit events

**Flyway migration V10** (created in this phase):

```sql
CREATE TABLE audit_events (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type   VARCHAR(64) NOT NULL,
    user_id      UUID REFERENCES users(id) ON DELETE SET NULL,
    ip_address   VARCHAR(64),
    user_agent   TEXT,
    metadata     JSONB,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_events_created_at ON audit_events(created_at DESC);
CREATE INDEX idx_audit_events_user_id    ON audit_events(user_id);
```

Recorded event types:

| Event | Trigger |
|-------|---------|
| `LOGIN_SUCCESS` | Password login completes (non-MFA path) |
| `LOGIN_FAILURE` | Bad password or unknown email |
| `MFA_SUCCESS` | TOTP code accepted |
| `MFA_FAILURE` | TOTP code rejected |
| `LOGOUT` | Session logout |
| `FEDERATED_LOGIN` | JIT OIDC user service completes |

`AuditService.log()` is synchronous but wrapped in try-catch — a logging failure never breaks the auth flow. IP resolution is `X-Forwarded-For`-aware (first hop used when header is present).

---

## Rate limiting (Bucket4j)

**Library**: `io.github.bucket4j:bucket4j-core:8.10.1` (package `io.github.bucket4j` — not `com.bucket4j`).

`RateLimitFilter` is **not** a `@Component` to avoid Spring Boot auto-registration. It is registered only as a servlet filter via `RateLimitConfig`:

```java
@Bean
public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration() {
    FilterRegistrationBean<RateLimitFilter> reg = new FilterRegistrationBean<>(new RateLimitFilter());
    reg.addUrlPatterns("/login", "/oauth2/token");
    reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
    return reg;
}
```

Buckets are stored in a `ConcurrentHashMap<String, Bucket>` keyed by IP address.

| Endpoint | Capacity | Refill |
|----------|----------|--------|
| `POST /login` | 10 requests | per 10-minute window (intervally) |
| `POST /oauth2/token` | 20 requests | per 1-minute window (intervally) |

- **Login limit exceeded**: redirect to `/login?error=rate_limited`
- **Token limit exceeded**: HTTP 429 JSON `{"error":"rate_limited"}` + `Retry-After: 60` header

The `login.html` error message block differentiates between `rate_limited`, `mfa_locked`, and generic credential failures using `th:if="${param.error[0] == 'rate_limited'}"`.
