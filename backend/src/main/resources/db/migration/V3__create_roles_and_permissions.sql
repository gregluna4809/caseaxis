-- =============================================================================
-- V3: roles and permissions tables
-- Seeded with role data in V6.
--
-- roles: named RBAC roles (ADMIN, SUPERVISOR, CASE_WORKER, AUDITOR).
--   Application code references roles by CODE, never by ID.
--
-- permissions: fine-grained action codes (e.g. case:create, case:assign).
--   Initial Phase 5 implementation uses role-level checks only.
--   This table is populated in Phase 5 (authentication implementation).
--   Defined here so the schema is complete for the role_permissions junction table.
-- =============================================================================

CREATE TABLE roles (
    id            UUID         NOT NULL,
    code          VARCHAR(50)  NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    description   TEXT,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_roles      PRIMARY KEY (id),
    CONSTRAINT uq_roles_code UNIQUE (code)
);

CREATE TRIGGER trg_roles_updated_at
    BEFORE UPDATE ON roles
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- permissions has no updated_at: permission definitions should be managed via
-- new migration rows, not in-place edits, to maintain an explicit change record.
CREATE TABLE permissions (
    id            UUID         NOT NULL,
    code          VARCHAR(100) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    resource      VARCHAR(50)  NOT NULL,
    action        VARCHAR(50)  NOT NULL,
    description   TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_permissions      PRIMARY KEY (id),
    CONSTRAINT uq_permissions_code UNIQUE (code)
);
