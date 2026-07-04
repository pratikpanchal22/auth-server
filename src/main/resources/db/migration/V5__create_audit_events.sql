CREATE TABLE audit_events (
    id         UUID                     PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID                     REFERENCES users(id) ON DELETE SET NULL,
    event_type VARCHAR(100)             NOT NULL,
    ip_address VARCHAR(45),
    user_agent TEXT,
    metadata   JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
