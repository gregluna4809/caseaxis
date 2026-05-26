-- =============================================================================
-- V2: users table
-- System operators: case workers, supervisors, admins, auditors.
-- Not to be confused with clients (the subjects of cases).
--
-- created_by is nullable to support the bootstrap ADMIN user who has no
-- preceding creator. All other inserts must supply created_by.
--
-- is_active and is_deleted are distinct states:
--   is_active = FALSE : user cannot log in (suspended/terminated), records intact
--   is_deleted = TRUE : user removed from operational view, records retained for FK integrity
-- =============================================================================

CREATE TABLE users (
    id             UUID         NOT NULL,
    username       VARCHAR(100) NOT NULL,
    email          VARCHAR(255) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    first_name     VARCHAR(100) NOT NULL,
    last_name      VARCHAR(100) NOT NULL,
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at  TIMESTAMPTZ,
    is_deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at     TIMESTAMPTZ,
    deleted_by     UUID,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by     UUID,
    updated_by     UUID,

    CONSTRAINT pk_users          PRIMARY KEY (id),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email    UNIQUE (email),

    -- Self-referential foreign keys.
    -- created_by is nullable for the bootstrap admin who has no preceding creator.
    CONSTRAINT fk_users_deleted_by FOREIGN KEY (deleted_by) REFERENCES users (id),
    CONSTRAINT fk_users_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_users_updated_by FOREIGN KEY (updated_by) REFERENCES users (id)
);

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Active user lookup for login checks, user assignment pickers, and role checks.
CREATE INDEX idx_users_is_active
    ON users (is_active)
    WHERE is_deleted = FALSE;
