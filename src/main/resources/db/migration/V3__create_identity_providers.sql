CREATE TABLE identity_providers (
    id                UUID                     PRIMARY KEY DEFAULT gen_random_uuid(),
    name              VARCHAR(100)             NOT NULL UNIQUE,
    issuer_url        VARCHAR(500)             NOT NULL,
    client_id         VARCHAR(255)             NOT NULL,
    client_secret_ref VARCHAR(500)             NOT NULL,
    scopes            VARCHAR(500)             NOT NULL DEFAULT 'openid,profile,email',
    enabled           BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
