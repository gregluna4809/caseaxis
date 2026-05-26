-- =============================================================================
-- V9: cases table — the primary business entity
--
-- Key design decisions documented here:
--
-- OWNERSHIP CONSTRAINT:
--   Every case must have at least one subject: a client, an organisation, or both.
--   A case with neither is not a case — it is an unstructured task.
--   Enforced at the database level by ck_cases_subject_required.
--
-- ASSIGNED_TO_ID DENORMALISATION:
--   cases.assigned_to_id and cases.assigned_at cache the current assignee for
--   query efficiency. The authoritative assignment history is in case_assignments.
--   These three fields must be updated atomically within a single @Transactional
--   service method whenever a reassignment occurs:
--     1. Close the prior case_assignments row (set unassigned_at, unassigned_by)
--     2. Insert a new case_assignments row
--     3. Update cases.assigned_to_id and cases.assigned_at
--   The uq_case_assignments_one_active index (V10) enforces at most one active
--   assignment at the database level as a safety net.
--
-- CASE NUMBER:
--   case_number is application-generated using case_number_seq (created in V7).
--   Format: CA-NNNNNN  (e.g. CA-000001)
--   The UNIQUE constraint enforces global uniqueness.
--
-- REOPENED_COUNT:
--   Cached on the case row for efficient reporting on problem cases.
--   Incremented by the service layer on each transition to REOPENED status.
--   The ck_cases_reopened_count constraint prevents negative values.
-- =============================================================================

CREATE TABLE cases (
    id              UUID         NOT NULL,
    case_number     VARCHAR(20)  NOT NULL,
    title           VARCHAR(500) NOT NULL,
    description     TEXT,
    status_id       UUID         NOT NULL,
    priority_id     UUID         NOT NULL,
    type_id         UUID         NOT NULL,
    organization_id UUID,
    client_id       UUID,
    assigned_to_id  UUID,
    assigned_at     TIMESTAMPTZ,
    due_date        DATE,
    resolved_at     TIMESTAMPTZ,
    closed_at       TIMESTAMPTZ,
    reopened_count  INTEGER      NOT NULL DEFAULT 0,
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at      TIMESTAMPTZ,
    deleted_by      UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      UUID         NOT NULL,
    updated_by      UUID,

    CONSTRAINT pk_cases                  PRIMARY KEY (id),
    CONSTRAINT uq_cases_case_number      UNIQUE (case_number),

    -- Every case must have at least one of: client_id, organization_id.
    CONSTRAINT ck_cases_subject_required CHECK (client_id IS NOT NULL OR organization_id IS NOT NULL),
    CONSTRAINT ck_cases_reopened_count   CHECK (reopened_count >= 0),

    CONSTRAINT fk_cases_status_id        FOREIGN KEY (status_id)       REFERENCES case_statuses   (id),
    CONSTRAINT fk_cases_priority_id      FOREIGN KEY (priority_id)     REFERENCES case_priorities (id),
    CONSTRAINT fk_cases_type_id          FOREIGN KEY (type_id)         REFERENCES case_types      (id),
    CONSTRAINT fk_cases_organization_id  FOREIGN KEY (organization_id) REFERENCES organizations   (id),
    CONSTRAINT fk_cases_client_id        FOREIGN KEY (client_id)       REFERENCES clients         (id),
    CONSTRAINT fk_cases_assigned_to_id   FOREIGN KEY (assigned_to_id)  REFERENCES users           (id),
    CONSTRAINT fk_cases_deleted_by       FOREIGN KEY (deleted_by)      REFERENCES users           (id),
    CONSTRAINT fk_cases_created_by       FOREIGN KEY (created_by)      REFERENCES users           (id),
    CONSTRAINT fk_cases_updated_by       FOREIGN KEY (updated_by)      REFERENCES users           (id)
);

CREATE TRIGGER trg_cases_updated_at
    BEFORE UPDATE ON cases
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Worker dashboard: open cases for a specific worker, optionally filtered by status.
-- Most frequent query in the system.
CREATE INDEX idx_cases_assignee_status
    ON cases (assigned_to_id, status_id)
    WHERE is_deleted = FALSE;

-- Status filter alone: dashboard aggregates, supervisor queue view, unassigned pool.
CREATE INDEX idx_cases_status_id
    ON cases (status_id)
    WHERE is_deleted = FALSE;

-- Organisation filter: "all cases for Acme Corp"
CREATE INDEX idx_cases_organization_id
    ON cases (organization_id)
    WHERE is_deleted = FALSE;

-- Client filter: "all cases for client Jane Smith"
CREATE INDEX idx_cases_client_id
    ON cases (client_id)
    WHERE is_deleted = FALSE;

-- Date range queries, recent cases list, time-based reporting.
CREATE INDEX idx_cases_created_at
    ON cases (created_at DESC)
    WHERE is_deleted = FALSE;

-- Overdue case detection: due_date < today AND case not in terminal state.
CREATE INDEX idx_cases_due_date
    ON cases (due_date)
    WHERE is_deleted = FALSE AND due_date IS NOT NULL;

-- Reporting: case distribution by type and status (e.g. open complaints by month).
CREATE INDEX idx_cases_type_status
    ON cases (type_id, status_id)
    WHERE is_deleted = FALSE;
