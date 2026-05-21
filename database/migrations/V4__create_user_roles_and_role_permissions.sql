-- =============================================================================
-- V4: user_roles and role_permissions junction tables
--
-- user_roles: tracks which roles a user currently holds and the full history
--   of role assignments and revocations. Rows are NEVER deleted.
--   Revocation is recorded by setting removed_at / removed_by.
--   The partial unique index prevents a user from holding the same active role twice.
--
-- role_permissions: maps permissions to roles.
--   Hard delete is appropriate here; role-permission changes are low-frequency
--   and captured in audit_logs at the service layer.
-- =============================================================================

CREATE TABLE user_roles (
    id           UUID        NOT NULL,
    user_id      UUID        NOT NULL,
    role_id      UUID        NOT NULL,
    assigned_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    assigned_by  UUID        NOT NULL,
    removed_at   TIMESTAMPTZ,
    removed_by   UUID,

    CONSTRAINT pk_user_roles          PRIMARY KEY (id),
    CONSTRAINT fk_user_roles_user     FOREIGN KEY (user_id)     REFERENCES users (id),
    CONSTRAINT fk_user_roles_role     FOREIGN KEY (role_id)     REFERENCES roles (id),
    CONSTRAINT fk_user_roles_assignby FOREIGN KEY (assigned_by) REFERENCES users (id),
    CONSTRAINT fk_user_roles_removeby FOREIGN KEY (removed_by)  REFERENCES users (id)
);

-- A user may not hold the same role simultaneously more than once.
-- Partial index on active rows (removed_at IS NULL) allows the same role
-- to be re-assigned after it has been revoked.
CREATE UNIQUE INDEX uq_user_roles_active
    ON user_roles (user_id, role_id)
    WHERE removed_at IS NULL;

-- Active role lookup for a user (authorization checks on every authenticated request).
CREATE INDEX idx_user_roles_user_active
    ON user_roles (user_id)
    WHERE removed_at IS NULL;

-- Role revocation history lookup ("when was user X's ADMIN role removed?")
CREATE INDEX idx_user_roles_user_id
    ON user_roles (user_id);

----------------------------------------------------------------------

CREATE TABLE role_permissions (
    id             UUID NOT NULL,
    role_id        UUID NOT NULL,
    permission_id  UUID NOT NULL,

    CONSTRAINT pk_role_permissions   PRIMARY KEY (id),
    CONSTRAINT uq_role_permissions   UNIQUE (role_id, permission_id),
    CONSTRAINT fk_rp_role            FOREIGN KEY (role_id)       REFERENCES roles (id),
    CONSTRAINT fk_rp_permission      FOREIGN KEY (permission_id) REFERENCES permissions (id)
);

CREATE INDEX idx_rp_role_id
    ON role_permissions (role_id);
