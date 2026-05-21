-- =============================================================================
-- V13: Supplementary indexes
--
-- All primary operational indexes were created inline with their respective
-- table migrations (V2–V12). This migration adds supplementary indexes that
-- support secondary query patterns: search, deduplication checks, reporting,
-- and audit filtering not covered by the primary access patterns.
--
-- These indexes are deliberately separated so they can be omitted during
-- schema validation runs without affecting table creation or constraint checks.
-- =============================================================================

-- Client email lookup.
-- Not a UNIQUE constraint (duplicate emails are legitimately possible across
-- unrelated clients in a high-volume operational system), but a B-tree index
-- supports deduplication checks and integration lookups efficiently.
CREATE INDEX idx_clients_email
    ON clients (email)
    WHERE is_deleted = FALSE AND email IS NOT NULL;

-- Client external_id lookup (cross-system integration, import deduplication).
CREATE INDEX idx_clients_external_id
    ON clients (external_id)
    WHERE is_deleted = FALSE AND external_id IS NOT NULL;

-- Problem case reporting: cases that have been reopened at least once.
-- reopened_count is cached on the cases row precisely to enable this pattern.
CREATE INDEX idx_cases_reopened
    ON cases (reopened_count DESC)
    WHERE is_deleted = FALSE AND reopened_count > 0;

-- Reporting: case volume over time segmented by type.
-- Supports queries like "how many APPLICATION cases were opened in Q1 2026?"
CREATE INDEX idx_cases_type_created_at
    ON cases (type_id, created_at DESC)
    WHERE is_deleted = FALSE;

-- Audit action filter.
-- Supports queries like "show me all case_status_changed events in the last 30 days".
-- Placed in V13 rather than V12 because this is a reporting-support index,
-- not a primary operational access pattern.
CREATE INDEX idx_al_action_occurred_at
    ON audit_logs (action, occurred_at DESC);
