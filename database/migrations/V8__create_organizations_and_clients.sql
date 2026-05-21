-- =============================================================================
-- V8: organizations and clients tables
--
-- organizations: external entities associated with cases.
--   Companies, government agencies, institutions.
--   external_id is a nullable unique reference from an external system.
--   Multiple NULLs are permitted by PostgreSQL's UNIQUE constraint behaviour.
--
-- clients: the individuals or entities for whom cases are opened.
--   A client may optionally belong to one organisation.
--   A client with no organisation_id is an independent/individual client.
-- =============================================================================

CREATE TABLE organizations (
    id             UUID         NOT NULL,
    name           VARCHAR(255) NOT NULL,
    external_id    VARCHAR(100),
    address_line1  VARCHAR(255),
    address_line2  VARCHAR(255),
    city           VARCHAR(100),
    state_province VARCHAR(100),
    postal_code    VARCHAR(20),
    country        VARCHAR(100) NOT NULL DEFAULT 'USA',
    phone          VARCHAR(50),
    email          VARCHAR(255),
    notes          TEXT,
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    is_deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at     TIMESTAMPTZ,
    deleted_by     UUID,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by     UUID         NOT NULL,
    updated_by     UUID,

    CONSTRAINT pk_organizations            PRIMARY KEY (id),
    CONSTRAINT uq_organizations_ext_id     UNIQUE (external_id),
    CONSTRAINT fk_organizations_deleted_by FOREIGN KEY (deleted_by) REFERENCES users (id),
    CONSTRAINT fk_organizations_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_organizations_updated_by FOREIGN KEY (updated_by) REFERENCES users (id)
);

CREATE TRIGGER trg_organizations_updated_at
    BEFORE UPDATE ON organizations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Organisation name search for autocomplete / case creation UI
CREATE INDEX idx_organizations_name
    ON organizations (name)
    WHERE is_deleted = FALSE;

-- Active organisation filter
CREATE INDEX idx_organizations_is_active
    ON organizations (is_active)
    WHERE is_deleted = FALSE;

----------------------------------------------------------------------

CREATE TABLE clients (
    id              UUID         NOT NULL,
    organization_id UUID,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    middle_name     VARCHAR(100),
    date_of_birth   DATE,
    email           VARCHAR(255),
    phone           VARCHAR(50),
    address_line1   VARCHAR(255),
    address_line2   VARCHAR(255),
    city            VARCHAR(100),
    state_province  VARCHAR(100),
    postal_code     VARCHAR(20),
    country         VARCHAR(100) NOT NULL DEFAULT 'USA',
    external_id     VARCHAR(100),
    notes           TEXT,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at      TIMESTAMPTZ,
    deleted_by      UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      UUID         NOT NULL,
    updated_by      UUID,

    CONSTRAINT pk_clients            PRIMARY KEY (id),
    CONSTRAINT fk_clients_org        FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_clients_deleted_by FOREIGN KEY (deleted_by)      REFERENCES users (id),
    CONSTRAINT fk_clients_created_by FOREIGN KEY (created_by)      REFERENCES users (id),
    CONSTRAINT fk_clients_updated_by FOREIGN KEY (updated_by)      REFERENCES users (id)
);

CREATE TRIGGER trg_clients_updated_at
    BEFORE UPDATE ON clients
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- All clients belonging to an organisation
CREATE INDEX idx_clients_org_id
    ON clients (organization_id)
    WHERE is_deleted = FALSE;

-- Last name search (primary client search pattern)
CREATE INDEX idx_clients_last_name
    ON clients (last_name)
    WHERE is_deleted = FALSE;
