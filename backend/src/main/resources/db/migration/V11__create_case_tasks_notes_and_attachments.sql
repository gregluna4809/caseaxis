-- =============================================================================
-- V11: case_tasks, case_notes, case_attachments
-- =============================================================================

-- -----------------------------------------------------------------------------
-- case_tasks
-- Actionable work items attached to a case.
-- Fully mutable with soft delete and updated_at maintenance.
-- completed_at / completed_by are set by the service layer when status
-- transitions to COMPLETED — they are not database-managed.
-- -----------------------------------------------------------------------------
CREATE TABLE case_tasks (
    id             UUID         NOT NULL,
    case_id        UUID         NOT NULL,
    title          VARCHAR(500) NOT NULL,
    description    TEXT,
    status_id      UUID         NOT NULL,
    assigned_to_id UUID,
    due_date       DATE,
    completed_at   TIMESTAMPTZ,
    completed_by   UUID,
    is_deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at     TIMESTAMPTZ,
    deleted_by     UUID,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by     UUID         NOT NULL,
    updated_by     UUID,

    CONSTRAINT pk_case_tasks        PRIMARY KEY (id),
    CONSTRAINT fk_ct_case_id        FOREIGN KEY (case_id)        REFERENCES cases        (id),
    CONSTRAINT fk_ct_status_id      FOREIGN KEY (status_id)      REFERENCES task_statuses (id),
    CONSTRAINT fk_ct_assigned_to_id FOREIGN KEY (assigned_to_id) REFERENCES users        (id),
    CONSTRAINT fk_ct_completed_by   FOREIGN KEY (completed_by)   REFERENCES users        (id),
    CONSTRAINT fk_ct_deleted_by     FOREIGN KEY (deleted_by)     REFERENCES users        (id),
    CONSTRAINT fk_ct_created_by     FOREIGN KEY (created_by)     REFERENCES users        (id),
    CONSTRAINT fk_ct_updated_by     FOREIGN KEY (updated_by)     REFERENCES users        (id)
);

CREATE TRIGGER trg_case_tasks_updated_at
    BEFORE UPDATE ON case_tasks
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- All tasks for a given case (primary access pattern for case detail view).
CREATE INDEX idx_ct_case_id
    ON case_tasks (case_id)
    WHERE is_deleted = FALSE;

-- Worker task workload by status (e.g. "my open tasks").
CREATE INDEX idx_ct_assigned_to_status
    ON case_tasks (assigned_to_id, status_id)
    WHERE is_deleted = FALSE;

-- Overdue task detection (due_date < today AND status not terminal).
CREATE INDEX idx_ct_due_date
    ON case_tasks (due_date)
    WHERE is_deleted = FALSE;

-- -----------------------------------------------------------------------------
-- case_notes
-- Free-text notes attached to a case. IMMUTABLE CONTENT.
--
-- Notes are evidence records, not editable text. The body, case_id, is_internal,
-- supersedes_note_id, created_by, and created_at cannot be changed after creation.
-- The trg_case_notes_immutable trigger enforces this at the database level.
--
-- Only soft-delete fields (is_deleted, deleted_at, deleted_by) may be updated.
--
-- CORRECTION WORKFLOW:
--   To correct an erroneous note:
--     1. Soft-delete the original note (UPDATE ... SET is_deleted=TRUE, deleted_at, deleted_by).
--     2. INSERT a new note with the corrected content.
--     3. Set new_note.supersedes_note_id = original_note.id.
--   The UI can display this chain with a "corrected" indicator.
--   The audit log captures both the deletion event and the new creation event.
--
-- No updated_at column: the only permitted change after INSERT is soft deletion.
-- -----------------------------------------------------------------------------
CREATE TABLE case_notes (
    id                 UUID        NOT NULL,
    case_id            UUID        NOT NULL,
    body               TEXT        NOT NULL,
    is_internal        BOOLEAN     NOT NULL DEFAULT FALSE,
    supersedes_note_id UUID,
    is_deleted         BOOLEAN     NOT NULL DEFAULT FALSE,
    deleted_at         TIMESTAMPTZ,
    deleted_by         UUID,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID        NOT NULL,

    CONSTRAINT pk_case_notes              PRIMARY KEY (id),
    CONSTRAINT fk_cn_case_id              FOREIGN KEY (case_id)            REFERENCES cases      (id),
    -- Self-referential: a correcting note points to the note it supersedes.
    CONSTRAINT fk_cn_supersedes_note_id   FOREIGN KEY (supersedes_note_id) REFERENCES case_notes (id),
    CONSTRAINT fk_cn_deleted_by           FOREIGN KEY (deleted_by)         REFERENCES users      (id),
    CONSTRAINT fk_cn_created_by           FOREIGN KEY (created_by)         REFERENCES users      (id)
);

-- Protect immutable fields. Permits soft-delete column updates only.
-- See prevent_note_body_update() in V1 for the protected field list.
CREATE TRIGGER trg_case_notes_immutable
    BEFORE UPDATE ON case_notes
    FOR EACH ROW EXECUTE FUNCTION prevent_note_body_update();

-- All notes for a given case (primary access: case detail note thread).
CREATE INDEX idx_cn_case_id
    ON case_notes (case_id)
    WHERE is_deleted = FALSE;

-- -----------------------------------------------------------------------------
-- case_attachments
-- Metadata record for files attached to a case.
-- The actual binary content is stored externally (filesystem or object storage).
-- This table stores the metadata required to locate and describe the file.
--
-- No updated_at: attachment metadata is immutable after upload.
-- The re-upload pattern is: soft-delete the old record, insert a new record.
--
-- storage_path supports both local filesystem paths (Phase 2 development) and
-- future object storage keys (e.g. an S3 key) without a schema change.
-- The storage abstraction lives in the application layer.
-- -----------------------------------------------------------------------------
CREATE TABLE case_attachments (
    id                UUID          NOT NULL,
    case_id           UUID          NOT NULL,
    original_filename VARCHAR(255)  NOT NULL,
    storage_path      VARCHAR(1000) NOT NULL,
    file_size_bytes   BIGINT,
    mime_type         VARCHAR(100),
    description       VARCHAR(500),
    is_deleted        BOOLEAN       NOT NULL DEFAULT FALSE,
    deleted_at        TIMESTAMPTZ,
    deleted_by        UUID,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by        UUID          NOT NULL,

    CONSTRAINT pk_case_attachments          PRIMARY KEY (id),
    CONSTRAINT ck_case_attachments_filesize CHECK (file_size_bytes IS NULL OR file_size_bytes >= 0),
    CONSTRAINT fk_catt_case_id              FOREIGN KEY (case_id)   REFERENCES cases (id),
    CONSTRAINT fk_catt_deleted_by           FOREIGN KEY (deleted_by) REFERENCES users (id),
    CONSTRAINT fk_catt_created_by           FOREIGN KEY (created_by) REFERENCES users (id)
);

-- All attachments for a given case.
CREATE INDEX idx_catt_case_id
    ON case_attachments (case_id)
    WHERE is_deleted = FALSE;
