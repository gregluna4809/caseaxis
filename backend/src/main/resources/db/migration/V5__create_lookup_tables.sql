-- =============================================================================
-- V5: Lookup / reference tables
-- Seeded with operational data in V6.
--
-- Design decisions:
--   - Lookup tables instead of PostgreSQL ENUMs to support metadata flags
--     (is_terminal, is_initial, sort_order) and to avoid ALTER TYPE lock overhead.
--   - No updated_at column: lookup values should be managed via new migration rows,
--     not silent in-place edits that bypass the change record.
--   - Use is_active to deactivate values; never DELETE lookup rows that are
--     referenced by existing cases or tasks.
-- =============================================================================

-- case_statuses
-- Represents the lifecycle states a case can be in.
-- is_initial = TRUE for the NEW state only.
-- is_terminal = TRUE for APPROVED, DENIED, CLOSED.
-- The service layer uses is_terminal to determine transition legality
-- without hard-coding status code strings in business logic.
CREATE TABLE case_statuses (
    id            UUID         NOT NULL,
    code          VARCHAR(50)  NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    description   TEXT,
    is_initial    BOOLEAN      NOT NULL DEFAULT FALSE,
    is_terminal   BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order    INTEGER      NOT NULL,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_case_statuses      PRIMARY KEY (id),
    CONSTRAINT uq_case_statuses_code UNIQUE (code)
);

-- case_priorities
-- LOW / MEDIUM / HIGH / CRITICAL
CREATE TABLE case_priorities (
    id            UUID        NOT NULL,
    code          VARCHAR(20) NOT NULL,
    display_name  VARCHAR(50) NOT NULL,
    sort_order    INTEGER     NOT NULL,
    is_active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_case_priorities      PRIMARY KEY (id),
    CONSTRAINT uq_case_priorities_code UNIQUE (code)
);

-- case_types
-- Categorises the nature of a case: Complaint, Application, Inquiry, Investigation, General.
-- New types can be added by inserting a new row in a migration — no code change required
-- unless the type needs specific workflow behaviour in the service layer.
CREATE TABLE case_types (
    id            UUID         NOT NULL,
    code          VARCHAR(50)  NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    description   TEXT,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order    INTEGER      NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_case_types      PRIMARY KEY (id),
    CONSTRAINT uq_case_types_code UNIQUE (code)
);

-- task_statuses
-- PENDING / IN_PROGRESS / COMPLETED / CANCELLED
-- is_terminal = TRUE for COMPLETED and CANCELLED.
CREATE TABLE task_statuses (
    id            UUID        NOT NULL,
    code          VARCHAR(20) NOT NULL,
    display_name  VARCHAR(50) NOT NULL,
    is_terminal   BOOLEAN     NOT NULL DEFAULT FALSE,
    sort_order    INTEGER     NOT NULL,
    is_active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_task_statuses      PRIMARY KEY (id),
    CONSTRAINT uq_task_statuses_code UNIQUE (code)
);
