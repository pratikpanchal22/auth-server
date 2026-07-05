# Phase-11: Admin UI — User + Client + IDP Management + Login Tracking

## What this phase does

Introduces a full admin panel (Tabler-based) for managing users, OAuth2 clients, and identity providers. Also adds login tracking columns to the `users` table so admins can see last sign-in date and consecutive failed attempts at a glance.

---

## What changed

| Before | After |
|--------|-------|
| No admin UI | Tabler-based admin panel at `/admin/**` (ADMIN role required) |
| No login tracking | `last_login_at`, `failed_attempts` columns on `users` |
| Clients only visible in DB | `/admin/clients` lists registered OAuth2 clients |
| IDPs only configurable via SQL | `/admin/idps` — full CRUD for identity providers |
| Users only manageable via SQL | `/admin/users` — list, create, edit, delete users |

---

## Admin panel structure

```
/admin/              → redirect to /admin/users
/admin/users         → user list table
/admin/users/new     → create user form
/admin/users/{id}/edit → edit user form
/admin/users/{id}/delete (POST) → delete user
/admin/clients       → registered OAuth2 client list (read-only)
/admin/idps          → IDP list
/admin/idps/new      → create IDP form
/admin/idps/{id}/edit → edit IDP form
/admin/idps/{id}/delete (POST) → delete IDP
```

---

## Login tracking

**Flyway migration V11** adds two columns:

```sql
ALTER TABLE users ADD COLUMN last_login_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN failed_attempts INT NOT NULL DEFAULT 0;
```

`LoginTrackingService` manages these fields:

| Method | Behaviour |
|--------|-----------|
| `recordSuccess(email)` | Sets `last_login_at = now()`, resets `failed_attempts = 0` |
| `recordFailure(email)` | Increments `failed_attempts` |
| `resetFailedAttempts(email)` | Sets `failed_attempts = 0` (used after MFA success) |

Called from `MfaAuthenticationSuccessHandler` (both MFA and non-MFA paths), `MfaController` (TOTP success), `JitOidcUserService` (federated login), and `LoginFailureHandler` (password failure).

---

## Security

`/admin/**` is gated by `.requestMatchers("/admin/**").hasRole("ADMIN")` in `SecurityConfig`. Admin users carry `ROLE_ADMIN` stored in the `user_roles` join table.

---

## Theme

Admin pages use [Tabler](https://tabler.io/) (`@tabler/core@1.0.0-beta21`) with Bootstrap 5 underneath. The theme toggle in the sidebar footer calls the same `toggleTheme()` from `theme.js`, but uses a Tabler icon element (`<i class="ti ti-sun/moon">`) instead of the emoji span used on user-facing pages. `theme.js` detects the element type at runtime.
