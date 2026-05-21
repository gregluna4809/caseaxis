-- =============================================================================
-- V7: Global case number sequence
--
-- Used by the application service layer to generate human-readable case numbers
-- in the format CA-NNNNNN (e.g. CA-000001, CA-042731).
--
-- Design decisions:
--   - Global sequence, never resets. Supports up to 999,999 cases before
--     the 6-digit padding overflows; increase to 7 digits proactively if
--     projections warrant it before reaching ~900K cases.
--   - The year is NOT encoded in the sequence. Creation year is always
--     available from cases.created_at and is optionally embedded in the
--     display label by the application (e.g. "CA-000001 (2025)").
--     A year-embedded non-resetting sequence was rejected because operators
--     expect CA-2026-000001 to mean the first case of 2026, which it would not.
--   - CACHE 1: no pre-allocation. Prevents gaps from cached-but-unused values
--     if the application restarts. Acceptable at the target insert rate.
--     Increase CACHE if case creation throughput becomes a bottleneck.
--
-- Application usage (Java):
--   long nextVal = jdbcTemplate.queryForObject("SELECT nextval('case_number_seq')", Long.class);
--   String caseNumber = String.format("CA-%06d", nextVal);
-- =============================================================================

CREATE SEQUENCE case_number_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;
