-- Seed admin user — password: changeme (BCrypt 10 rounds)
-- Change this password immediately after first login.
INSERT INTO users (id, email, password_hash, auth_type, mfa_enabled, active)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'admin@localhost',
    '$2y$10$vI80n97lRKr6aee/eLFPdeAOAmBsuGyDdyAaS57ZNqAsnDk1xuWMS',
    'LOCAL',
    FALSE,
    TRUE
);

INSERT INTO user_roles (user_id, role)
VALUES ('00000000-0000-0000-0000-000000000001', 'ADMIN');
