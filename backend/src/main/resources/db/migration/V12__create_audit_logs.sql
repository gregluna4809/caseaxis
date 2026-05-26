-- =============================================================================
-- V12: audit_logs
--
-- Immutable record of all significant business actions. Append-only.
-- UPDATE and DELETE are blocked by the prevent_update_delete() trigger.
--
-- PRIMARY KEY DESIGN:
--   PRIMARY KEY (id, occurred_at) — composite key.
--   occurred_at is included so that future declarative range partitioning
--   by occurred_at can be added without requiring a PK migration.
--   In PostgreSQL, the partition key must be part of the primary key.
--   The cost of carrying occurred_at in the PK is negligible (8 extra bytes).
--
-- ENTITY_TYPE VALUES (stable domain constants, NOT Java class names):
--   Java class names change during refactors; audit records must not.
--   Use these lowercase constants — they match the table names and are stable:
--     case              → cases
--     user              → users
--     organization      → organizations
--     client            → clients
--     case_task         → case_tasks
--     case_note         → case_notes
--     case_attachment   → case_attachments
--     case_assignment   → case_assignments
--     user_role         → user_roles
--
-- STANDARD ACTION VALUES (for reference):
--   case_created             case_status_changed      case_assigned
--   case_reassigned          case_unassigned          case_closed
--   case_reopened            case_deleted             case_priority_changed
--   case_type_changed        case_due_date_changed
--   note_created             note_deleted
--   task_created             task_assigned            task_completed
--   task_deleted
--   attachment_uploaded      attachment_deleted
--   user_created             user_deactivated         user_deleted
--   user_role_assigned       user_role_removed
--
-- JSONB FIELDS:
--   old_values / new_values: state before and after a change.
--   metadata: contextual information (IP address, session ID, user agent, reason).
--   JSONB is used for flexibility — audit schema must not require a migration
--   every time a new auditable event type is introduced.
--   Audit trail queries are filtered by entity_type + entity_id + occurred_at range,
--   not by value content, so JSONB query overhead is not on the hot path.
--
-- NO FK ON entity_id:
--   Audit records must persist beyond the lifetime of the referenced entity.
--   A FK would either prevent deletion or cascade-delete audit history.
--   Neither is acceptable for a compliance record.
-- =============================================================================

CREATE TABLE audit_logs (
    id           UUID         NOT NULL,
    occurred_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actor_id     UUID,
    entity_type  VARCHAR(50)  NOT NULL,
    entity_id    UUID         NOT NULL,
    action       VARCHAR(100) NOT NULL,
    old_values   JSONB,
    new_values   JSONB,
    metadata     JSONB,

    -- Composite PK enables future range partitioning by occurred_at.
    CONSTRAINT pk_audit_logs       PRIMARY KEY (id, occurred_at),
    -- actor_id is nullable for system-initiated events (scheduled jobs, migrations).
    -- No FK on entity_id by design — see header comment.
    CONSTRAINT fk_audit_logs_actor FOREIGN KEY (actor_id) REFERENCES users (id)
);

-- Fully immutable: no UPDATE or DELETE permitted.
CREATE TRIGGER trg_audit_logs_immutable
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION prevent_update_delete();

-- Primary access pattern: all audit events for a specific entity (e.g. audit trail for case X).
CREATE INDEX idx_al_entity
    ON audit_logs (entity_type, entity_id, occurred_at DESC);

-- Actor history: all actions taken by a specific user.
CREATE INDEX idx_al_actor_id
    ON audit_logs (actor_id, occurred_at DESC);

-- Time-range scans: compliance reports, recent activity feeds, administrative queries.
CREATE INDEX idx_al_occurred_at
    ON audit_logs (occurred_at DESC);
