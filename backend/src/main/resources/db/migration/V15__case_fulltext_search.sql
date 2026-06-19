-- =============================================================================
-- V15: Index-backed case full-text search
--
-- Choice: PostgreSQL full-text search instead of pg_trgm.
-- Case search is a keyword search over operational case text, not arbitrary
-- substring matching. A generated tsvector plus partial GIN index gives an
-- index-backed plan for active case search at production scale.
--
-- case_number is included with high weight to preserve direct case-number
-- search behavior without keeping leading-wildcard LIKE predicates. title and
-- description are the primary text body for the generated vector.
-- =============================================================================

ALTER TABLE cases
    ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (
        setweight(to_tsvector('simple', coalesce(case_number, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(description, '')), 'B')
    ) STORED;

CREATE INDEX idx_cases_search_vector
    ON cases USING GIN (search_vector)
    WHERE is_deleted = FALSE;

-- Operator plan check after migration on a populated environment:
-- EXPLAIN ANALYZE
-- SELECT c.id, c.case_number, c.title
-- FROM cases c
-- WHERE c.is_deleted = FALSE
--   AND c.search_vector @@ to_tsquery('english', 'appeal:* & overdue:*')
-- ORDER BY c.updated_at DESC
-- LIMIT 20;
