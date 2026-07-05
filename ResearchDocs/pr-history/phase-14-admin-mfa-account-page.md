# Phase-14: Admin-Enforced MFA + MfaEnrollmentFilter + Account Settings Page

## What this phase does

Allows admins to require MFA for specific users, enforces enrollment via a security filter, and gives users a self-service account settings page where they can enroll in or disable MFA.

---

## What changed

| Before | After |
|--------|-------|
| MFA always opt-in | Admin can set `mfa_required = true` per user |
| No enrollment gate | `MfaEnrollmentFilter` redirects required-but-unenrolled users to `/mfa/enroll` |
| No user-facing settings page | `/account` — profile card + MFA card with self-service controls |
| Navbar username plain text | Username is a link to `/account` |

---

## Flyway migration V13

```sql
ALTER TABLE users ADD COLUMN mfa_required BOOLEAN NOT NULL DEFAULT FALSE;
```

---

## `MfaEnrollmentFilter`

`OncePerRequestFilter` registered only in the Spring Security chain (not as a servlet filter — `FilterRegistrationBean(setEnabled(false))` prevents double-registration).

```
shouldNotFilter: /mfa/**, /css/**, /js/**, /images/**, /webjars/**,
                 /oauth2/**, /hrd/**, /actuator/**, /login, /logout, /error, /access-denied
```

`doFilterInternal` logic:

```
principal anonymous?              → skip (let Spring Security redirect to /login)
principal has PRE_MFA authority?  → skip (TOTP challenge in progress)
mfaRequired == true
  AND mfaEnabled == false?        → sendRedirect("/mfa/enroll")
otherwise                         → chain.doFilter (normal flow)
```

The filter is added after `AnonymousAuthenticationFilter` so that anonymous requests are already populated before the check.

---

## Admin user form

The "Require MFA" checkbox in `/admin/users/{id}/edit`:

```html
<label class="form-check">
    <input class="form-check-input" type="checkbox"
           name="mfaRequired" value="true" th:checked="*{mfaRequired}">
    <span class="form-check-label">Require MFA</span>
</label>
<div class="form-hint">User will be redirected to enroll in MFA on next sign-in.</div>
```

A user with `mfa_required = true` and `mfa_enabled = true` sees "Required" in the admin table's MFA column. They cannot self-disable MFA via the account page — the Disable button is hidden.

---

## Account settings page (`/account`)

`AccountController` resolves the caller's email from either `OidcUser` (federated) or `UserDetails` (local) principal:

```java
private static String resolveEmail(Object principal) {
    return switch (principal) {
        case OidcUser u  -> u.getEmail();
        case UserDetails u -> u.getUsername();
        default -> "unknown";
    };
}
```

**GET `/account`** model attributes:

| Attribute | Source |
|-----------|--------|
| `email` | resolved from principal |
| `authType` | `user.getAuthType().name()` |
| `mfaEnabled` | `user.isMfaEnabled()` |
| `mfaRequired` | `user.isMfaRequired()` |
| `recoveryCodesRemaining` | `recoveryCodeRepository.countByUserIdAndUsedFalse(id)` (0 if not enrolled) |

**POST `/account/mfa/disable`** — clears `mfaEnabled`, `totpSecretRef`, `totpFailedAttempts`, deletes all recovery codes. Blocked (no-op) when `mfaRequired = true`.

The account page `account.html` uses Bootstrap 5 + `auth.css` (same as login/enroll pages) and shows:
- **Profile card**: email + auth type badge
- **Two-factor authentication card**: enrollment status badge, recovery codes remaining (red when ≤ 2), Set up / Disable buttons

---

## QR code alignment fix

`QRCode.js` renders a `<canvas>` (block-inline element). Bootstrap's `text-center` (`text-align`) does not center block elements. Fixed by replacing `class="mb-3 text-center"` with `class="mb-3 d-flex justify-content-center"` on the QR code container in `enroll.html`.
