-- =============================================================================
-- V1: Shared trigger utility functions
-- All trigger functions are defined here for central maintainability.
-- These functions are referenced by triggers created in later migrations.
-- =============================================================================

-- set_updated_at()
-- Maintains the updated_at column on mutable business tables.
-- Applied via BEFORE UPDATE trigger on every table that carries updated_at.
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- prevent_update_delete()
-- Rejects any UPDATE or DELETE on fully immutable tables.
-- Applied to: case_status_history, audit_logs.
-- These tables are append-only; the database enforces this, not just application code.
CREATE OR REPLACE FUNCTION prevent_update_delete()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION
        'Table "%" is immutable. UPDATE and DELETE operations are not permitted.',
        TG_TABLE_NAME;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- prevent_note_body_update()
-- Protects immutable fields on case_notes.
-- case_notes are evidence records. Only the soft-delete columns
-- (is_deleted, deleted_at, deleted_by) may be updated after creation.
-- All content and provenance fields are frozen at insert time.
CREATE OR REPLACE FUNCTION prevent_note_body_update()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.body IS DISTINCT FROM OLD.body THEN
        RAISE EXCEPTION 'case_notes.body is immutable and cannot be modified after creation.';
    END IF;
    IF NEW.case_id IS DISTINCT FROM OLD.case_id THEN
        RAISE EXCEPTION 'case_notes.case_id is immutable and cannot be modified after creation.';
    END IF;
    IF NEW.is_internal IS DISTINCT FROM OLD.is_internal THEN
        RAISE EXCEPTION 'case_notes.is_internal is immutable and cannot be modified after creation.';
    END IF;
    IF NEW.supersedes_note_id IS DISTINCT FROM OLD.supersedes_note_id THEN
        RAISE EXCEPTION 'case_notes.supersedes_note_id is immutable and cannot be modified after creation.';
    END IF;
    IF NEW.created_by IS DISTINCT FROM OLD.created_by THEN
        RAISE EXCEPTION 'case_notes.created_by is immutable and cannot be modified after creation.';
    END IF;
    IF NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'case_notes.created_at is immutable and cannot be modified after creation.';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
