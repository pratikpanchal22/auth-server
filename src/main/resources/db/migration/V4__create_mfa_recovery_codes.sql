CREATE TABLE mfa_recovery_codes (
    id         UUID                     PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID                     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash  VARCHAR(255)             NOT NULL,
    used       BOOLEAN                  NOT NULL DEFAULT FALSE,
    used_at    TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
