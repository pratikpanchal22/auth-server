-- client_id is already unique programmatically (Spring Auth Server enforces this),
-- but the column has no formal UNIQUE constraint for FK reference.
ALTER TABLE oauth2_registered_client
    ADD CONSTRAINT uq_orc_client_id UNIQUE (client_id);

CREATE TABLE client_ui_metadata (
    client_id    VARCHAR(100) NOT NULL PRIMARY KEY
                     REFERENCES oauth2_registered_client(client_id) ON DELETE CASCADE,
    display_name VARCHAR(100) NOT NULL,
    description  VARCHAR(255),
    launch_url   VARCHAR(500) NOT NULL,
    icon         VARCHAR(50)  NOT NULL DEFAULT 'apps',
    visible      BOOLEAN      NOT NULL DEFAULT FALSE
);
