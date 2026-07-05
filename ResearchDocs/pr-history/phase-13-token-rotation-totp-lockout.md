# Phase-13: Refresh Token Rotation + TOTP Lockout

## What this phase does

Enables refresh token rotation so each token can only be used once, and adds TOTP lockout — after 4 consecutive failed TOTP attempts the user's MFA is locked and requires admin intervention to unlock.

---

## What changed

| Before | After |
|--------|-------|
| Refresh tokens reusable indefinitely | `reuseRefreshTokens = false` — each use issues a new token |
| No TOTP brute-force protection | `totp_failed_attempts` column; lockout after N=4 failures |
| No TOTP unlock flow | Admin can unlock via `/admin/users/{id}/unlock-totp` |
| MFA locked state not visible | Admin users table shows "Locked" badge (danger) when `totpFailedAttempts >= 4` |

---

## Refresh token rotation

**Flyway migration V12** patches existing DB rows:

```sql
UPDATE oauth2_registered_client
SET token_settings = replace(
    token_settings,
    '"settings.token.reuse-refresh-tokens":true',
    '"settings.token.reuse-refresh-tokens":false'
)
WHERE token_settings LIKE '%"settings.token.reuse-refresh-tokens":true%';
```

`token_settings` is stored as TEXT (not JSONB), so a targeted `replace()` is simpler and safer than JSONB path manipulation. `ClientDataLoader` already sets `reuseRefreshTokens(false)` for new clients — this migration aligns existing rows.

When rotation is active, Spring Authorization Server:
1. Issues a new refresh token on each use
2. Invalidates the old refresh token immediately
3. Rejects any re-use of the old token with `invalid_grant`

---

## TOTP lockout

**Flyway migration V12** also adds:

```sql
ALTER TABLE users ADD COLUMN totp_failed_attempts INT NOT NULL DEFAULT 0;
```

Lockout threshold: **N = 4** (constant `MAX_TOTP_ATTEMPTS = 4` in `MfaController`).

### Failure flow

```
POST /mfa/challenge → wrong code
  ↓
user.totpFailedAttempts++
  ↓
totpFailedAttempts >= 4?
  ├── NO  → stay on challenge page with ?error
  └── YES → SecurityContextHolder.clearContext()
             session.invalidate()
             redirect /login?error=mfa_locked
```

A pre-check at the top of `MfaController.challenge()` also handles re-authentication: if a user logs in again with the correct password but `totpFailedAttempts >= 4`, they are locked out immediately without being shown the TOTP form.

### Admin unlock

`POST /admin/users/{id}/unlock-totp` sets `totpFailedAttempts = 0`. The unlock button (`ti ti-lock-open`) only appears in the admin users table when `mfaEnabled == true AND totpFailedAttempts >= 4`.

---

## `login.html` error differentiation

```html
<span th:if="${param.error[0] == 'mfa_locked'}">
    Account locked — too many failed verification attempts. Contact your administrator.
</span>
```
