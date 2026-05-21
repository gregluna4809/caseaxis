-- =============================================================================
-- V6: Seed data for all lookup tables and initial roles
--
-- UUIDs are generated at migration execution time via gen_random_uuid().
-- These values will differ across environments, which is intentional and correct.
-- Application code MUST reference all lookup rows by CODE, never by ID.
-- Example: query "SELECT id FROM case_statuses WHERE code = 'NEW'" at startup,
--          or use a repository lookup method — never embed hardcoded UUIDs.
-- =============================================================================

-- -------------------------------------------------------------------------
-- case_statuses (9 values)
-- Lifecycle states ordered from NEW through to terminal states.
-- is_initial = TRUE on NEW only. is_terminal = TRUE on APPROVED, DENIED, CLOSED.
-- -------------------------------------------------------------------------
INSERT INTO case_statuses (id, code, display_name, description, is_initial, is_terminal, sort_order)
VALUES
    (gen_random_uuid(), 'NEW',
     'New',
     'Case has been created and awaits assignment.',
     TRUE,  FALSE, 1),

    (gen_random_uuid(), 'ASSIGNED',
     'Assigned',
     'Case has been assigned to a case worker and awaits review.',
     FALSE, FALSE, 2),

    (gen_random_uuid(), 'IN_REVIEW',
     'In Review',
     'Case is actively under review by the assigned worker.',
     FALSE, FALSE, 3),

    (gen_random_uuid(), 'PENDING_INFO',
     'Pending Info',
     'Awaiting additional information from the client or a third party.',
     FALSE, FALSE, 4),

    (gen_random_uuid(), 'ESCALATED',
     'Escalated',
     'Case has been escalated for supervisory or senior review.',
     FALSE, FALSE, 5),

    (gen_random_uuid(), 'APPROVED',
     'Approved',
     'Case outcome: approved. Terminal state — no further transitions except REOPEN.',
     FALSE, TRUE,  6),

    (gen_random_uuid(), 'DENIED',
     'Denied',
     'Case outcome: denied. Terminal state — no further transitions except REOPEN.',
     FALSE, TRUE,  7),

    (gen_random_uuid(), 'CLOSED',
     'Closed',
     'Case is closed. Terminal state — may be reopened.',
     FALSE, TRUE,  8),

    (gen_random_uuid(), 'REOPENED',
     'Reopened',
     'Previously closed or resolved case that has been reopened. Re-enters the active workflow.',
     FALSE, FALSE, 9);

-- -------------------------------------------------------------------------
-- case_priorities (4 values)
-- -------------------------------------------------------------------------
INSERT INTO case_priorities (id, code, display_name, sort_order)
VALUES
    (gen_random_uuid(), 'LOW',      'Low',      1),
    (gen_random_uuid(), 'MEDIUM',   'Medium',   2),
    (gen_random_uuid(), 'HIGH',     'High',     3),
    (gen_random_uuid(), 'CRITICAL', 'Critical', 4);

-- -------------------------------------------------------------------------
-- case_types (5 values)
-- New types can be added by inserting a row in a future migration.
-- -------------------------------------------------------------------------
INSERT INTO case_types (id, code, display_name, description, sort_order)
VALUES
    (gen_random_uuid(), 'COMPLAINT',
     'Complaint',
     'A formal complaint or grievance filed against a person or organisation.',
     1),

    (gen_random_uuid(), 'APPLICATION',
     'Application',
     'An application for a benefit, service, permit, or approval.',
     2),

    (gen_random_uuid(), 'INQUIRY',
     'Inquiry',
     'A general request for information, guidance, or clarification.',
     3),

    (gen_random_uuid(), 'INVESTIGATION',
     'Investigation',
     'A formal investigation into a reported issue, incident, or potential violation.',
     4),

    (gen_random_uuid(), 'GENERAL',
     'General',
     'General or unclassified case. Use when no specific type applies at creation time.',
     5);

-- -------------------------------------------------------------------------
-- task_statuses (4 values)
-- -------------------------------------------------------------------------
INSERT INTO task_statuses (id, code, display_name, is_terminal, sort_order)
VALUES
    (gen_random_uuid(), 'PENDING',     'Pending',     FALSE, 1),
    (gen_random_uuid(), 'IN_PROGRESS', 'In Progress', FALSE, 2),
    (gen_random_uuid(), 'COMPLETED',   'Completed',   TRUE,  3),
    (gen_random_uuid(), 'CANCELLED',   'Cancelled',   TRUE,  4);

-- -------------------------------------------------------------------------
-- roles (4 values)
-- Application code references roles by CODE (e.g. 'ADMIN'), never by ID.
-- -------------------------------------------------------------------------
INSERT INTO roles (id, code, display_name, description)
VALUES
    (gen_random_uuid(), 'ADMIN',
     'Administrator',
     'Full system access. Manages users, roles, and system configuration.'),

    (gen_random_uuid(), 'SUPERVISOR',
     'Supervisor',
     'Oversees case workers. Can assign, reassign, and escalate cases. Views all cases in scope.'),

    (gen_random_uuid(), 'CASE_WORKER',
     'Case Worker',
     'Handles assigned cases. Creates and updates cases, tasks, and notes.'),

    (gen_random_uuid(), 'AUDITOR',
     'Auditor',
     'Read-only access to cases, audit logs, and reports. Cannot create or modify any records.');
