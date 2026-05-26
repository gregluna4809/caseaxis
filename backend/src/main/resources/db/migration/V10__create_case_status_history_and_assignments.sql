-- =============================================================================
-- V10: case_status_history and case_assignments
--
-- Both tables are historical operational ledgers. Neither uses soft delete.
--
-- case_status_history:
--   Fully immutable. A row is inserted at case creation (from_status_id NULL,
--   to_status_id = NEW) and on every subsequent status transition.
--   UPDATE and DELETE are blocked by the prevent_update_delete() trigger.
--   This table is a first-class operational concept, not just an audit log entry —
--   case lifecycle timeline queries are a primary access pattern.
--
-- case_assignments:
--   Historical record of every assignment and reassignment.
--   Active assignment is identified by unassigned_at IS NULL.
--   When a case is reassigned, the service layer sets unassigned_at and
--   unassigned_by on the prior row before inserting the new row.
--   uq_case_assignments_one_active enforces at most one active assignment
--   per case at the database level. This is a correctness constraint —
--   if a service layer bug attempts to create a second active assignment row
--   without closing the first, the DB will reject the insert.
-- =============================================================================

CREATE TABLE case_status_history (
    id             UUID        NOT NULL,
    case_id        UUID        NOT NULL,
    from_status_id UUID,
    to_status_id   UUID        NOT NULL,
    changed_by     UUID        NOT NULL,
    changed_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    reason         TEXT,

    CONSTRAINT pk_case_status_history    PRIMARY KEY (id),
    CONSTRAINT fk_csh_case_id            FOREIGN KEY (case_id)        REFERENCES cases         (id),
    -- from_status_id is nullable: the initial creation event has no prior status.
    CONSTRAINT fk_csh_from_status_id     FOREIGN KEY (from_status_id) REFERENCES case_statuses (id),
    CONSTRAINT fk_csh_to_status_id       FOREIGN KEY (to_status_id)   REFERENCES case_statuses (id),
    CONSTRAINT fk_csh_changed_by         FOREIGN KEY (changed_by)     REFERENCES users         (id)
);

-- Fully immutable: no UPDATE or DELETE permitted.
CREATE TRIGGER trg_case_status_history_immutable
    BEFORE UPDATE OR DELETE ON case_status_history
    FOR EACH ROW EXECUTE FUNCTION prevent_update_delete();

-- Primary access: full lifecycle timeline for case X, chronological.
CREATE INDEX idx_csh_case_id_changed_at
    ON case_status_history (case_id, changed_at DESC);

-- Analytics: how many cases transitioned into a given status within a time range.
CREATE INDEX idx_csh_to_status_changed_at
    ON case_status_history (to_status_id, changed_at DESC);

----------------------------------------------------------------------

CREATE TABLE case_assignments (
    id            UUID        NOT NULL,
    case_id       UUID        NOT NULL,
    assignee_id   UUID        NOT NULL,
    assigned_by   UUID        NOT NULL,
    assigned_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    unassigned_at TIMESTAMPTZ,
    unassigned_by UUID,
    notes         TEXT,

    CONSTRAINT pk_case_assignments    PRIMARY KEY (id),
    CONSTRAINT fk_ca_case_id          FOREIGN KEY (case_id)       REFERENCES cases (id),
    CONSTRAINT fk_ca_assignee_id      FOREIGN KEY (assignee_id)   REFERENCES users (id),
    CONSTRAINT fk_ca_assigned_by      FOREIGN KEY (assigned_by)   REFERENCES users (id),
    CONSTRAINT fk_ca_unassigned_by    FOREIGN KEY (unassigned_by) REFERENCES users (id)
);

-- DB-level correctness constraint: at most one active assignment row per case.
-- unassigned_at IS NULL identifies the currently active assignment.
-- This partial unique index is the authoritative enforcement point.
-- The service layer is also responsible, but the index is the final guard.
CREATE UNIQUE INDEX uq_case_assignments_one_active
    ON case_assignments (case_id)
    WHERE unassigned_at IS NULL;

-- Full assignment history for a given case.
CREATE INDEX idx_ca_case_id
    ON case_assignments (case_id);

-- Current active assignments for a given worker.
CREATE INDEX idx_ca_assignee_active
    ON case_assignments (assignee_id)
    WHERE unassigned_at IS NULL;
