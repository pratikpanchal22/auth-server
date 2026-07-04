CREATE TABLE users (
    id              UUID                     PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255)             NOT NULL UNIQUE,
    password_hash   VARCHAR(255),
    auth_type       VARCHAR(20)              NOT NULL,
    mfa_enabled     BOOLEAN                  NOT NULL DEFAULT FALSE,
    totp_secret_ref VARCHAR(500),
    active          BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
