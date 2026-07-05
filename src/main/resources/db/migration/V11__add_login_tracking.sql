ALTER TABLE users
    ADD COLUMN last_login_at  TIMESTAMPTZ,
    ADD COLUMN failed_attempts INT NOT NULL DEFAULT 0;
