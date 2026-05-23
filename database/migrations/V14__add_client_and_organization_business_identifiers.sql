-- =============================================================================
-- V14: human-readable business identifiers for clients and organizations
--
-- UUID primary keys remain the internal relational identifiers. These business
-- identifiers are stable, human-friendly labels for normal user workflows.
-- Format:
--   clients       CL-NNNNNNNNN
--   organizations ORG-NNNNNNNNN
-- =============================================================================

CREATE SEQUENCE client_number_seq
    AS BIGINT
    MINVALUE 1
    MAXVALUE 999999999
    START WITH 1
    INCREMENT BY 1
    NO CYCLE;

CREATE SEQUENCE organization_code_seq
    AS BIGINT
    MINVALUE 1
    MAXVALUE 999999999
    START WITH 1
    INCREMENT BY 1
    NO CYCLE;

ALTER TABLE clients
    ADD COLUMN client_number VARCHAR(12);

ALTER TABLE organizations
    ADD COLUMN organization_code VARCHAR(13);

UPDATE clients
SET client_number = 'CL-' || lpad(nextval('client_number_seq')::text, 9, '0')
WHERE client_number IS NULL;

UPDATE organizations
SET organization_code = 'ORG-' || lpad(nextval('organization_code_seq')::text, 9, '0')
WHERE organization_code IS NULL;

ALTER TABLE clients
    ALTER COLUMN client_number SET NOT NULL,
    ALTER COLUMN client_number SET DEFAULT ('CL-' || lpad(nextval('client_number_seq')::text, 9, '0')),
    ADD CONSTRAINT uq_clients_client_number UNIQUE (client_number),
    ADD CONSTRAINT ck_clients_client_number_format CHECK (client_number ~ '^CL-[0-9]{9}$');

ALTER TABLE organizations
    ALTER COLUMN organization_code SET NOT NULL,
    ALTER COLUMN organization_code SET DEFAULT ('ORG-' || lpad(nextval('organization_code_seq')::text, 9, '0')),
    ADD CONSTRAINT uq_organizations_organization_code UNIQUE (organization_code),
    ADD CONSTRAINT ck_organizations_organization_code_format CHECK (organization_code ~ '^ORG-[0-9]{9}$');

CREATE INDEX idx_clients_client_number
    ON clients (client_number)
    WHERE is_deleted = FALSE;

CREATE INDEX idx_organizations_organization_code
    ON organizations (organization_code)
    WHERE is_deleted = FALSE;
