CREATE INDEX idx_users_email               ON users(email);
CREATE INDEX idx_audit_events_user_id      ON audit_events(user_id);
CREATE INDEX idx_audit_events_event_type   ON audit_events(event_type);
CREATE INDEX idx_audit_events_created_at   ON audit_events(created_at DESC);
CREATE INDEX idx_mfa_recovery_codes_user   ON mfa_recovery_codes(user_id) WHERE used = FALSE;
