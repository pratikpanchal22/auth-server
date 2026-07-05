-- TOTP lockout counter
ALTER TABLE users
    ADD COLUMN totp_failed_attempts INT NOT NULL DEFAULT 0;

-- Enable refresh token rotation for existing registered clients.
-- New clients already get reuseRefreshTokens(false) via ClientDataLoader.
UPDATE oauth2_registered_client
SET token_settings = replace(
    token_settings,
    '"settings.token.reuse-refresh-tokens":true',
    '"settings.token.reuse-refresh-tokens":false'
)
WHERE token_settings LIKE '%"settings.token.reuse-refresh-tokens":true%';
