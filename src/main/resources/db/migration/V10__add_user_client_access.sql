CREATE TABLE user_allowed_clients (
    user_id   UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    client_id VARCHAR(255) NOT NULL,
    PRIMARY KEY (user_id, client_id)
);
