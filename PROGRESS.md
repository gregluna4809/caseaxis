# PROGRESS.md
# CaseAxis Engineering Progress Log

## Purpose

This document serves as the official engineering progress journal for **CaseAxis**.

Its purpose is to maintain a clear, chronological record of:

- architectural decisions
- implementation milestones
- completed work
- major changes
- blockers encountered
- rationale behind technical choices
- lessons learned

This document exists to prevent project amnesia.

It should allow a future reviewer (or the project owner) to understand:

- what was built
- when it was built
- why it was built that way
- what changed over time

---

# Logging Rules

## Record Major Milestones

Log:

- architecture decisions
- stack changes
- schema milestones
- major feature completion
- performance work
- security implementation
- deployment milestones

Do NOT clutter this file with trivial edits.

---

## Be Honest

Never claim:

- tests passed unless verified
- builds succeeded unless verified
- migrations executed unless verified
- integrations confirmed unless tested

This is engineering history, not marketing.

---

## Preferred Entry Format

Each major entry should include:

- date
- milestone
- summary
- rationale
- notable decisions
- blockers/issues
- next steps

---

# Project Timeline

---

## 2026-05-20
### Project Foundation Established

#### Milestone
CaseAxis officially initiated.

---

#### Summary

Project concept established as a flagship enterprise software engineering portfolio project.

The objective is to design and implement a realistic production-style case workflow and decision management platform.

Initial planning emphasized:

- enterprise realism
- relational database engineering
- backend architecture maturity
- workflow modeling
- auditability
- explainable design
- interview defensibility

This is intentionally NOT a tutorial project.

---

#### Naming Decision

Selected project name:

**CaseAxis**

Rationale:

The name conveys:

- operational centrality
- workflow coordination
- enterprise software positioning
- professional branding

CaseAxis suggests a platform around which operational case processing revolves.

Alternative names considered included:

- CaseLogic
- CaseLedger
- CaseForge
- CivicLogic

CaseAxis selected for strongest enterprise product identity.

---

#### Stack Decisions

Approved stack:

Frontend:
- React
- TypeScript
- Vite

Backend:
- Java 21
- Spring Boot
- Spring Security

Database:
- PostgreSQL

Schema Management:
- Flyway

Authentication:
- JWT

Testing:
- JUnit
- integration testing

Seed Tooling:
- Python
- Faker

---

#### Architectural Decision

Selected architecture:

**Modular Monolith**

Rationale:

Rejected premature distributed architecture.

Reasons:

- lower complexity
- easier debugging
- faster local iteration
- simpler deployment model
- realistic first-generation enterprise architecture
- stronger interview defensibility

Explicitly deferred:

- microservices
- Kafka
- Redis
- Elasticsearch
- cloud orchestration
- distributed messaging

---

#### Governance Established

Foundational governance documents created:

- GREG.md
- AGENTS.md
- ARCHITECTURE.md
- ROADMAP.md
- PROGRESS.md

Purpose:

Prevent architectural drift.

Ensure consistent AI-assisted development.

Create engineering discipline from project inception.

---

#### Development Philosophy Established

Core principles adopted:

- production realism over tutorial simplicity
- maintainability over cleverness
- migrations over schema drift
- auditability as first-class concern
- explainability over opaque complexity
- quality over speed

---

#### AI Development Model

Defined development workflow:

Claude Code:
architecture / planning / analysis / documentation

Codex:
implementation / migrations / tests / focused engineering tasks

Project owner retains architectural control.

AI serves as engineering acceleration, not autonomous decision authority.

---

#### Scale Expectations

Target design assumptions:

- 1,000+ users
- 50,000+ organizations/entities
- 500,000+ cases
- millions of related records

System design must account for:

- indexing
- pagination
- reporting cost
- audit growth
- query performance

---

#### Risks Identified

Known risks:

1. Scope creep
2. Overengineering
3. AI architectural drift
4. premature optimization
5. documentation drift
6. unfinished implementation

Mitigation:

strict governance + phased delivery

---

#### Current Status

Phase:

**FOUNDATION COMPLETE**

Project maturity:

Concept / architecture approved

Implementation:

Not started

---

#### Next Steps

Immediate priorities:

1. Define domain entities
2. Design ERD
3. Establish naming conventions
4. Design relational schema
5. Plan indexing strategy
6. Define audit schema

---

## 2026-05-20
### Documentation Cleanup

#### Milestone
Foundation documents reviewed, formatted, and prepared for implementation phase.

#### Summary

All six foundation files reviewed for formatting artifacts, contradictions, and implementation readiness. No architecture or content decisions were changed — only cleanup of code fence wrappers and AI-generated sign-offs introduced during initial document creation.

#### Changes

- **AGENTS.md**: Removed markdown code fence wrapper artifact; removed duplicate `# AGENTS.md` H1 heading; removed AI sign-off text
- **ARCHITECTURE.md**: Removed AI-generated intro line at top; removed two trailing code fence artifacts and sign-off
- **README.md**: Removed markdown code fence wrapper; fixed H1 from `` # `README.md` `` to `# CaseAxis`; corrected "In Progress" section to "Next Milestone"
- **ROADMAP.md**: Marked README.md deliverable as `[x]` complete
- **PROGRESS.md**: Added this entry; removed placeholder "Future Entries" text

#### Contradictions Resolved

1. ROADMAP.md listed README.md as `[ ]` incomplete despite the file existing — corrected to `[x]`
2. README.md "In Progress" section claimed domain modeling was underway — corrected to "Next Milestone" since work has not started
3. AGENTS.md, ARCHITECTURE.md, and README.md each contained markdown code fence wrapper artifacts that would break rendered display — removed

#### Decisions

No architecture changes made. All technology choices, governance hierarchy, engineering principles, module boundaries, case lifecycle states, RBAC roles, scale targets, and roadmap phases are preserved exactly as authored.

#### Noted (No Change)

The AGENTS.md governance hierarchy places GREG.md at priority 5 (lowest). This is defensible: GREG.md is a background/preferences document, not an operative rule document. Explicit user instruction is priority 1, so Gregory's live direction always takes precedence regardless.

#### Next Steps

Design the initial CaseAxis domain model and relational schema (Phase 1 of ROADMAP.md).

---

## 2026-05-20
### Domain Model & Relational Schema Design

#### Milestone
Phase 1 domain modeling complete. Design document produced.

#### Summary

Designed the initial relational schema for CaseAxis across all required business
domains. Produced `docs/DATABASE_DESIGN.md` as the authoritative design reference for
Phase 2 (migrations).

#### Tables Designed (18 total)

| Domain | Tables |
|---|---|
| Identity & Access | users, roles, permissions, user_roles, role_permissions |
| Lookup / Reference | case_statuses, case_priorities, case_types, task_statuses |
| Organizations & Clients | organizations, clients |
| Cases | cases, case_status_history, case_assignments |
| Tasks / Notes / Attachments | case_tasks, case_notes, case_attachments |
| Audit | audit_logs |

#### Key Decisions

**Primary keys:** UUID v4 (`gen_random_uuid()`) across all tables. Prevents ID
enumeration. Known tradeoff: index fragmentation at scale; acceptable at 500K cases.

**Timestamps:** `TIMESTAMPTZ` everywhere. Shared `set_updated_at()` trigger maintains
`updated_at`. Business tables carry `created_by` and `updated_by`.

**Soft delete:** `is_deleted + deleted_at + deleted_by` on seven business tables.
History and audit tables are immutable — no soft delete applied to them.

**Lookup tables over enums:** `case_statuses`, `case_priorities`, `case_types`,
`task_statuses` are relational lookup tables, not PostgreSQL ENUMs. The `is_terminal`
flag on `case_statuses` enables lifecycle queries without hard-coded status lists.

**Case number:** Human-readable format `CA-YYYY-NNNNNN` generated from a global
PostgreSQL sequence. Year is cosmetic — the sequence never resets.

**Denormalized current assignee:** `cases.assigned_to_id` caches the current assignee
for efficient workload queries. Full history lives in `case_assignments`. Both must be
updated atomically in the assignment service.

**Audit log design:** Single `audit_logs` table with JSONB `old_values`/`new_values`.
No FK on `entity_id` — audit records must outlive the entities they reference.

**Separated case status history:** `case_status_history` is a first-class table, not
just an audit log entry. Status timeline queries are a core operational pattern.

**`case_types` added:** Not in original ARCHITECTURE.md entity list but required for
meaningful filtering and reporting. Negligible cost (one join), high operational value.

#### Indexing Strategy

Partial indexes on `is_deleted = FALSE` for all soft-deleted business tables.
Composite indexes for the two most common query patterns: worker workload by status
(`assigned_to_id, status_id`) and case type reporting (`type_id, status_id`).
Audit log indexed on `(entity_type, entity_id, occurred_at DESC)`.
Full strategy documented in DATABASE_DESIGN.md.

#### Open Questions

1. Should cases require `client_id` or `organization_id` (at least one)?
2. Should notes be immutable after creation (compliance policy)?
3. Is `case_type` always required or nullable for some case types?
4. Audit log partition strategy — quarterly or annual by `occurred_at`?
5. Should `user_roles` support an `expires_at` for temporary access?

#### Risks Identified

- UUID v4 index fragmentation at high audit log write rates
- Denormalized `assigned_to_id` requires transactional discipline
- JSONB audit queries are slow for analytical use cases

#### Next Steps

Phase 2: Write Flyway migrations in the sequence defined in DATABASE_DESIGN.md.
Start with `V1__create_users.sql` through `V11__create_indexes.sql`.

---

## 2026-05-20
### Database Design Review

#### Milestone
Critical design review completed. DATABASE_DESIGN.md updated and approved for
Phase 2 migration implementation.

#### Approach

Each design decision was challenged independently. Decisions that survived scrutiny
were explicitly defended; decisions that did not were changed with documented rationale.

#### Changes Made

**UUID v4 → UUID v7 (application-generated)**
`gen_random_uuid()` produces randomly distributed values. On high-insert tables
(`audit_logs`, `case_status_history`) this causes B-tree index fragmentation at scale.
UUID v7 (time-ordered) eliminates this without changing the PostgreSQL column type.
Generation handled via `uuid-creator` Java library. No DB schema change required.

**Case ownership CHECK constraint added**
`cases` table had both `client_id` and `organization_id` nullable with no enforcement.
A case with neither a client nor an organization is not a valid case — it is a task.
Added `CHECK (client_id IS NOT NULL OR organization_id IS NOT NULL)` to enforce this
at the database level.

**`case_notes` made immutable**
Notes had `updated_at` and `updated_by`, implying they could be edited. In a
compliance-oriented case management system, notes are evidence. Edits obscure the
timeline and are indistinguishable from retroactive manipulation. Removed update columns.
Added `supersedes_note_id UUID REFERENCES case_notes(id)` for the correction workflow
(soft-delete the erroneous note, create a corrected note with the supersedes link).

**`user_roles` revocation tracking added**
Role revocation had no timestamp or actor record. A deleted row is invisible.
Added `removed_at TIMESTAMPTZ` and `removed_by UUID` to `user_roles`. Role assignment
history is now directly queryable. Changed unique constraint to a partial unique index
on `(user_id, role_id) WHERE removed_at IS NULL`.

**`case_assignments` unique partial index added**
The at-most-one-active-assignment invariant was application-enforced only. A service
layer bug could create two active `case_assignments` rows for the same case with no
database-level guard. Added `UNIQUE INDEX uq_case_assignments_one_active ON
case_assignments(case_id) WHERE unassigned_at IS NULL`.

**`audit_logs` entity_type naming changed**
Original design used Java class names (`Case`, `CaseTask`). Java classes get renamed
during refactors; audit records do not. Changed to stable lowercase domain constants
that match table names: `case`, `case_task`, `case_note`, etc.

**`audit_logs` composite PK for partition readiness**
Original PK was `(id)`. PostgreSQL declarative range partitioning requires the
partition key to be in the PK. Changed to `PRIMARY KEY (id, occurred_at)` to enable
future `occurred_at` range partitioning without a breaking migration.

**Case number format simplified**
`CA-YYYY-NNNNNN` with a global (non-resetting) sequence was ambiguous: the year implies
year-segmentation but the sequence was global. Changed to `CA-NNNNNN`. Year information
is always available from `created_at`. Simplified format; unambiguous.

#### Defended Decisions (challenged, kept)

- Lookup tables over PostgreSQL ENUMs (`is_terminal` flag alone justifies it)
- `assigned_to_id` denormalization on `cases` (query benefit is real; risk is managed)
- JSONB for audit `old_values`/`new_values` (acceptable for non-hot audit paths)
- `case_status_history` as a first-class table (lifecycle timeline is a primary query)
- Soft delete on all business entities
- No SLA, queue, or tag tables (premature for Phase 1)

#### Migration Readiness

DATABASE_DESIGN.md is approved for Phase 2 migration implementation.
Migration sequence: V1 through V13 as defined in the document.

#### Next Steps

Write Flyway migrations following the V1–V13 sequence in DATABASE_DESIGN.md.

---

## 2026-05-20
### Phase 2 — Flyway Migrations Written

#### Milestone
All V1–V13 Flyway migrations written. Schema fully specified in SQL. Awaiting execution
against a live PostgreSQL instance (Phase 4 backend setup concern).

#### Summary

Implemented all 18 tables, lookup data, shared trigger functions, sequences, constraints,
and indexes documented in `docs/DATABASE_DESIGN.md`. The migration sequence is
dependency-ordered and Flyway-compatible.

#### Migration Sequence

| File | Contents |
|---|---|
| V1 | Shared trigger functions: `set_updated_at()`, `prevent_update_delete()`, `prevent_note_body_update()` |
| V2 | `users` — self-referential FKs for created_by/updated_by/deactivated_by, updated_at trigger |
| V3 | `roles`, `permissions` — roles has updated_at trigger; permissions is migration-managed |
| V4 | `user_roles`, `role_permissions` — `removed_at`/`removed_by` revocation tracking; `uq_user_roles_active` partial unique index |
| V5 | Lookup tables: `case_statuses`, `case_priorities`, `case_types`, `task_statuses` |
| V6 | Seed lookup data — 9 statuses, 4 priorities, 5 case types, 4 task statuses, 4 roles via `gen_random_uuid()` |
| V7 | `case_number_seq` — `START 1, NO MAXVALUE, CACHE 1` |
| V8 | `organizations`, `clients` — updated_at triggers, partial indexes |
| V9 | `cases` — CHECK constraints, 9 FKs, updated_at trigger, 7 indexes |
| V10 | `case_status_history` (immutable), `case_assignments` (`uq_case_assignments_one_active`) |
| V11 | `case_tasks` (mutable), `case_notes` (immutable body trigger, `supersedes_note_id`), `case_attachments` |
| V12 | `audit_logs` — composite PK `(id, occurred_at)`, no FK on `entity_id`, immutability trigger, 3 indexes |
| V13 | Supplementary indexes for search, deduplication, and reporting |

#### Schema Object Counts

- Tables: 18
- Trigger functions (shared): 3
- Per-table triggers: 16
- Sequences: 1 (`case_number_seq`)
- CHECK constraints: 3 (`cases` subject required, `cases` reopened_count, `case_attachments` file size)
- Partial unique indexes: 2 (`uq_user_roles_active`, `uq_case_assignments_one_active`)
- Operational indexes: ~35 total (inline with table definitions + V13 supplementary)
- FK constraints: ~50 total

#### Notable Implementation Details

- `prevent_note_body_update()` permits only `is_deleted/deleted_at/deleted_by` updates on `case_notes`; all other columns are protected
- `audit_logs` composite PK `(id, occurred_at)` enables future range partitioning without a breaking migration
- `uq_case_assignments_one_active` is the database-level guard against double-assignment bugs
- V6 seed data uses `gen_random_uuid()` (not UUID v7) — acceptable because seed lookup values are always looked up by `code`, not `id`
- Application must generate all UUIDs using `UuidCreator.getTimeOrderedEpoch()` (uuid-creator library) before insert

#### Execution Status

**EXECUTED AND VALIDATED.** See Phase 2 validation entry below.

#### Next Steps

Phase 3: Python + Faker seed data engine. Or Phase 4: Spring Boot backend scaffold (either
order is valid; backend foundation unblocks seed data execution against a real database).

---

## 2026-05-20
### Phase 2 — Migration Execution and Validation

#### Milestone
All 13 Flyway migrations executed against a live PostgreSQL 16 instance. All validations
passed. No defects found.

#### Environment

- PostgreSQL 16.13 running in Docker (postgres:16 image)
- Flyway OSS 12.6.1 (flyway/flyway Docker image)
- Java 21 (Eclipse Adoptium JDK 21.0.11) — available on host
- Docker Desktop 29.4.3

#### Migration Execution

```
Successfully applied 13 migrations to schema "public", now at version v13 (execution time 00:00.209s)
```

All 13 migrations applied on the first attempt. Zero errors, zero retries required.

Flyway schema history: V1–V13, all `success = TRUE`.

#### Validation Results

| Check | Result |
|---|---|
| All 18 tables present | PASS |
| 9 case_statuses seeded (1 initial, 3 terminal) | PASS |
| 4 case_priorities seeded | PASS |
| 5 case_types seeded | PASS |
| 4 task_statuses seeded (2 terminal) | PASS |
| 4 roles seeded | PASS |
| 3 shared trigger functions present | PASS |
| 11 trigger entries (9 distinct triggers) | PASS |
| 34 operational indexes present | PASS |
| `uq_case_assignments_one_active` partial unique index | PASS |
| `uq_user_roles_active` partial unique index | PASS |
| `case_number_seq` (START 1, NO MAXVALUE, CACHE 1) | PASS |
| `audit_logs` composite PK `(id, occurred_at)` | PASS |
| `audit_logs` has NO FK on `entity_id` | PASS |

#### Behavioral Tests

| Test | Expected | Actual |
|---|---|---|
| Insert `cases` with no client_id or organization_id | `ck_cases_subject_required` EXCEPTION | EXCEPTION raised ✓ |
| UPDATE `case_notes.body` | `prevent_note_body_update` EXCEPTION | EXCEPTION raised ✓ |
| UPDATE `case_notes` soft-delete columns | Permitted | UPDATE succeeded ✓ |
| UPDATE `audit_logs` row | `prevent_update_delete` EXCEPTION | EXCEPTION raised ✓ |
| DELETE `audit_logs` row | `prevent_update_delete` EXCEPTION | EXCEPTION raised ✓ |
| Insert 2nd active `case_assignments` for same case | `uq_case_assignments_one_active` EXCEPTION | EXCEPTION raised ✓ |
| UPDATE `cases.title` advances `updated_at` | `set_updated_at()` fires | `updated_at` advanced ✓ |
| `nextval('case_number_seq')` three times | 1, 2, 3 | 1, 2, 3 ✓ |

#### Defects Found

None.

#### Next Steps

Phase 3 (Seed Data Engine) or Phase 4 (Backend Foundation). Phase 4 is recommended first:
the Spring Boot scaffold is required before the seed generator can run against the application
layer, and it establishes the repository layer that seed scripts will target.

---

## 2026-05-20
### Phase 4 — Backend Foundation Scaffold

#### Milestone
Spring Boot backend project created, compiled, tested, and started successfully against a
live PostgreSQL 16 instance. Health endpoint verified. Security rules verified.

#### Build Tool Decision
**Maven** chosen over Gradle. Rationale: dominant convention for enterprise Spring Boot
projects; `pom.xml` is more explicit and more recognizable to reviewers; Spring Boot's own
documentation defaults to Maven. Maven Wrapper (mvnw/mvnw.cmd) generated via
`mvn wrapper:wrapper` using Maven 3.9.15.

#### Stack
- Spring Boot 3.4.4
- Spring 6.2.5
- Spring Security 6.4.4
- Hibernate ORM 6.6.11.Final
- HikariCP 5.1.0
- Flyway 10.20.1
- uuid-creator 5.3.3
- Lombok 1.18.36
- Tomcat 10.1.39 (embedded)
- Java 21.0.11 (Eclipse Adoptium Temurin)

#### Files Created

| File | Purpose |
|---|---|
| `backend/pom.xml` | Maven build descriptor; Spring Boot 3.4.4 parent |
| `backend/src/main/java/com/caseaxis/CaseAxisApplication.java` | Spring Boot entry point |
| `backend/src/main/java/com/caseaxis/health/HealthController.java` | `GET /api/health` endpoint |
| `backend/src/main/java/com/caseaxis/security/SecurityConfig.java` | Security filter chain (stateless, permits /api/health, 401 on all else) |
| `backend/src/main/java/com/caseaxis/common/response/ApiResponse.java` | Standard API response wrapper with Lombok |
| `backend/src/main/java/com/caseaxis/common/exception/ResourceNotFoundException.java` | 404 exception |
| `backend/src/main/java/com/caseaxis/common/exception/GlobalExceptionHandler.java` | Global @RestControllerAdvice handler |
| `backend/src/main/java/com/caseaxis/common/util/UuidGenerator.java` | UUID v7 utility via `UuidCreator.getTimeOrderedEpoch()` |
| `backend/src/main/resources/application.yml` | App config: datasource, JPA (ddl-auto=none), Flyway locations |
| `backend/src/test/java/com/caseaxis/health/HealthControllerTest.java` | @WebMvcTest slice test (no DB required) |
| `backend/mvnw`, `backend/mvnw.cmd`, `backend/.mvn/wrapper/` | Maven wrapper 3.3.4 |
| Placeholder packages (config, auth, users, roles, cases, organizations, clients, audit, common/validation) | Empty directories with .gitkeep |

#### Package Structure
```
com.caseaxis/
├── CaseAxisApplication.java
├── health/             HealthController
├── security/           SecurityConfig
├── common/
│   ├── exception/      GlobalExceptionHandler, ResourceNotFoundException
│   ├── response/       ApiResponse
│   └── util/           UuidGenerator
├── config/             (placeholder)
├── auth/               (placeholder — Phase 5 JWT)
├── users/              (placeholder — Phase 6)
├── roles/              (placeholder — Phase 6)
├── cases/              (placeholder — Phase 6)
├── organizations/      (placeholder — Phase 6)
├── clients/            (placeholder — Phase 6)
└── audit/              (placeholder — Phase 7)
```

#### Defects Encountered and Fixed

**1. XML comment syntax error in pom.xml**
Decorative `<!-- --- -->` dividers contain `--` which is illegal inside XML comment bodies.
Fixed by rewriting pom.xml with clean single-line XML comments.

**2. Spring Security 6.x returns 403 instead of 401 for unauthenticated requests**
Without an explicit `AuthenticationEntryPoint`, Spring Security 6.x defaults to 403 for
unauthenticated access to protected endpoints. REST APIs must return 401.
Fixed by adding `.exceptionHandling(ex -> ex.authenticationEntryPoint(...sendError(401)))`.
Validated by test: `protectedEndpoint_returnsUnauthorizedWithoutCredentials`.

**3. Port conflict: native Windows PostgreSQL 16 and Docker container both on port 5432**
Root cause: a native Windows PostgreSQL 16 installation (`C:\Program Files\PostgreSQL\16`)
was already running on port 5432. Spring Boot connected to the native instance, not the
Docker container, causing SCRAM-SHA-256 auth failures.
Resolution: created the `caseaxis` database and user on the native PostgreSQL, ran
Flyway V1–V13 migrations against it via `host.docker.internal`, then started Spring Boot.
Note: the `application.yml` JDBC URL defaults to `localhost:5432`. The native PostgreSQL
is now the authoritative development database.

#### Test Results
```
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Test 1: `health_returnsOkWithExpectedBody` — PASSED
Test 2: `protectedEndpoint_returnsUnauthorizedWithoutCredentials` — PASSED

#### Runtime Validation
```
GET /api/health  → HTTP 200
Body: {"success":true,"data":{"service":"caseaxis-backend","status":"UP"},"timestamp":"..."}

GET /api/cases   → HTTP 401
```

#### Flyway at Startup
```
Successfully validated 13 migrations (execution time 00:00.021s)
Current version of schema "public": 13
Schema "public" is up to date. No migration necessary.
```

#### Startup Time
```
Started CaseAxisApplication in 2.035 seconds (process running for 2.208)
```

#### Next Steps

Phase 5 (Authentication) or Phase 6 (Core Case Engine). Recommended: Phase 5 first —
JWT authentication is required before any case/user endpoints can be implemented
securely. Phase 5 adds: UserDetailsService, JWT filter, login endpoint, token issuance.

---

### Phase 5 — Authentication & Authorization

**Date:** 2026-05-21

**Status:** COMPLETE

#### Goal

Implement JWT-based authentication. Protect all endpoints except `/api/health` and
`/api/auth/login`. Issue tokens on successful login. Validate tokens on every request.

#### Implementation Summary

**Dependencies added to pom.xml:**
- `io.jsonwebtoken:jjwt-api:0.12.5`
- `io.jsonwebtoken:jjwt-impl:0.12.5` (runtime)
- `io.jsonwebtoken:jjwt-jackson:0.12.5` (runtime)

**Configuration added (application.yml):**
```yaml
application:
  jwt:
    secret: ${JWT_SECRET:change-this-in-production-minimum-32-bytes-required}
    expiration-ms: ${JWT_EXPIRATION_MS:86400000}
```
Default token lifetime: 24 hours. JWT secret taken from UTF-8 bytes directly
(no base64 encoding required; JJWT enforces minimum 256 bits = 32 bytes).

**New files:**

| File | Purpose |
|---|---|
| `users/User.java` | JPA entity — partial mapping of `users` table (id, username, email, passwordHash, firstName, lastName, active, deleted, createdAt, updatedAt) |
| `roles/Role.java` | JPA entity — minimal mapping of `roles` table (id, code, active) |
| `users/UserRepository.java` | `findByUsernameAndDeletedFalse(String)` + native SQL `findActiveRoleCodesByUserId(UUID)` for role loading |
| `security/CaseAxisUserDetailsService.java` | `UserDetailsService` implementation; loads user + active roles via repository |
| `security/JwtService.java` | JJWT 0.12.x token generation and validation |
| `security/JwtAuthenticationFilter.java` | `OncePerRequestFilter`; extracts Bearer token, validates, sets `SecurityContext` |
| `auth/LoginRequest.java` | Request record — username + password (both `@NotBlank`) |
| `auth/LoginResponse.java` | Response record — token string |
| `auth/AuthController.java` | `POST /api/auth/login` — authenticates via `AuthenticationManager`, returns JWT |
| `auth/AdminUserInitializer.java` | `ApplicationRunner`; creates admin/admin on first startup if no users exist (JdbcTemplate-based; handles self-referential `assigned_by` in `user_roles`) |

**Updated files:**

| File | Change |
|---|---|
| `security/SecurityConfig.java` | Added `PasswordEncoder` bean, `AuthenticationManager` bean, JWT filter registration, `/api/auth/login` to permitAll |
| `common/exception/GlobalExceptionHandler.java` | Added `BadCredentialsException` handler returning 401 |
| `test/health/HealthControllerTest.java` | Added `@MockBean` for `JwtService` and `CaseAxisUserDetailsService` (filter dependencies); `JwtAuthenticationFilter` remains a real bean so filter chain runs correctly in `@WebMvcTest` slice |
| `test/auth/AuthControllerTest.java` | `@SpringBootTest` integration tests for full auth flow |

#### Design Decisions

**No `DaoAuthenticationProvider` bean exposed:** Spring Security auto-creates it from the
`UserDetailsService` and `PasswordEncoder` beans. Exposing it explicitly caused a noisy
Spring Security warning. Removed to let Spring Security's autoconfiguration handle it.

**JdbcTemplate for admin seeder:** Using JPA would require managing all NOT NULL columns
through the entity and handling the schema's self-referential `assigned_by` constraint
(user_roles.assigned_by is NOT NULL, FK to users). JdbcTemplate INSERT handles this
cleanly by using the admin user's own UUID as `assigned_by`.

**Native SQL for role loading:** The `user_roles` table has a `removed_at IS NULL`
active-assignment filter. JPQL can't express this cleanly without a `UserRole` entity.
A native query in `UserRepository` is direct and correct.

#### Test Results

```
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Tests:
- `HealthControllerTest` (2): health endpoint 200, unauthenticated protected endpoint 401
- `AuthControllerTest` (4): login success returns token, login failure 401, no-token 401, valid-token passes security filter

#### Runtime Validation (manual)

```
POST /api/auth/login {"username":"admin","password":"admin"}
→ 200 {"success":true,"data":{"token":"eyJ..."}}

GET /api/cases (no token)
→ 401

GET /api/cases (Bearer token)
→ 500 (no handler yet — security filter passed, auth confirmed)

POST /api/auth/login {"username":"admin","password":"wrong"}
→ 401
```

#### Next Steps

Phase 6 — Core Case Engine: JPA entities for cases, assignments, tasks, notes;
service layer; CRUD endpoints. All protected — valid JWT required.

---

## 2026-05-22
### Phase 6 — Core Case Engine + Sub-Resources

#### Milestone
Full case CRUD, assignment workflow, status transitions, case notes, case tasks, and
attachment metadata endpoints implemented, tested (45/45), and manually validated.

#### Summary

Implemented the complete Phase 6 case engine in two passes:

**Pass 1 (Core Case Engine):** Cases, assignments, and status transitions.

**Pass 2 (Sub-resources):** Case notes (immutable), case tasks (mutable), and attachment
metadata registration. All three respect the schema immutability model defined in V11.

#### Endpoints Implemented (17 total)

| Method | Path | Feature |
|---|---|---|
| `GET` | `/api/health` | Health check (Phase 4) |
| `POST` | `/api/auth/login` | JWT login (Phase 5) |
| `POST` | `/api/cases` | Create case |
| `GET` | `/api/cases` | List cases (paginated) |
| `GET` | `/api/cases/{id}` | Get case detail |
| `POST` | `/api/cases/{id}/assign` | Assign case to user |
| `POST` | `/api/cases/{id}/status` | Transition case status |
| `POST` | `/api/cases/{id}/notes` | Create note |
| `GET` | `/api/cases/{id}/notes` | List notes |
| `DELETE` | `/api/cases/{id}/notes/{noteId}` | Soft-delete note |
| `POST` | `/api/cases/{id}/tasks` | Create task |
| `GET` | `/api/cases/{id}/tasks` | List tasks |
| `GET` | `/api/cases/{id}/tasks/{taskId}` | Get task |
| `PUT` | `/api/cases/{id}/tasks/{taskId}` | Update task |
| `DELETE` | `/api/cases/{id}/tasks/{taskId}` | Soft-delete task |
| `POST` | `/api/cases/{id}/attachments` | Register attachment metadata |
| `GET` | `/api/cases/{id}/attachments` | List attachments |
| `DELETE` | `/api/cases/{id}/attachments/{attachmentId}` | Soft-delete attachment |

#### Key Technical Decisions

**Status machine enforced in service layer.** Transition legality is validated against
a static `Map<String, Set<String>>` in `CaseService`. Invalid transitions return `409 Conflict`.

**Notes are immutable.** `CaseNote` entity marks content columns as `updatable = false`.
The database `trg_case_notes_immutable` trigger is the authoritative guard. Only
`is_deleted`, `deleted_at`, and `deleted_by` can be updated — used by the soft-delete path.

**Task completion metadata auto-set.** When `statusCode` transitions to `COMPLETED` for
the first time, `completedAt` and `completedBy` are recorded by the service. Subsequent
updates do not overwrite the original completion timestamp.

**Attachment metadata only.** No file upload — the API registers metadata for files
already written to storage by the caller. `storage_path` supports both local filesystem
paths and future object storage keys (e.g. S3 key) without a schema change.

#### Files Created (Phase 6, Pass 2)

Entities: `TaskStatus`, `CaseNote`, `CaseTask`, `CaseAttachment`  
Repositories: `TaskStatusRepository`, `CaseNoteRepository`, `CaseTaskRepository`, `CaseAttachmentRepository`  
DTOs: `CreateCaseNoteRequest`, `CaseNoteResponse`, `CreateCaseTaskRequest`, `UpdateCaseTaskRequest`, `CaseTaskResponse`, `CreateCaseAttachmentRequest`, `CaseAttachmentResponse`  
Services: `CaseNoteService`, `CaseTaskService`, `CaseAttachmentService`  
Controllers: `CaseNoteController`, `CaseTaskController`, `CaseAttachmentController`  
Tests: `CaseNoteControllerTest` (9), `CaseTaskControllerTest` (11), `CaseAttachmentControllerTest` (8)

#### Test Results

```
Tests run: 45, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

#### Manual Validation

Live server validated on native PostgreSQL 16 (localhost:5432):

```
POST /api/cases/{id}/notes  → 201, note body + timestamps correct
GET  /api/cases/{id}/notes  → 200, returns note array
POST /api/cases/{id}/tasks  → 201, IN_PROGRESS status resolved correctly
POST /api/cases/{id}/attachments → 201, filename + mimeType + fileSizeBytes persisted
```

#### Next Steps

Phase 7 — React frontend. API contract is documented in `docs/API_CONTRACT.md`.
Seed data engine (Python + Faker) is also ready to be built now that the schema and
service layer are stable.

---

## 2026-05-22
### API Contract Documented

#### Milestone
`docs/API_CONTRACT.md` written covering all 17 implemented endpoints.

#### Summary

Produced the definitive API reference document before frontend development begins.
Covers all endpoints with: method, path, auth requirements, request body field table,
response shape with example JSON, validation notes, and error codes. Includes the full
status transition matrix, all reference data codes, and a Known Inconsistencies section
documenting five design tradeoffs that frontend developers should be aware of.

#### Key Inconsistencies Documented

1. Sub-resource list endpoints return plain arrays; `GET /api/cases` returns a Spring `Page`
2. `CaseDetailResponse` omits `updatedBy` (field exists in DB, not yet exposed)
3. DELETE responses return `"data": null` (Jackson `@JsonInclude(NON_NULL)` omits it on the wire)
4. No individual GET for notes or attachments (tasks have one; notes/attachments do not)
5. Status codes are case-insensitive on input, always uppercase on output

#### Next Steps

Begin frontend development (React + TypeScript + Vite) against the documented contract.

---

## 2026-05-23
### Business Identifiers Added for Clients and Organizations

#### Milestone
Human-readable business identifiers were added while preserving UUID primary keys as internal database identifiers.

#### Implementation Details

- Added Flyway migration `V14__add_client_and_organization_business_identifiers.sql`.
- Added `clients.client_number` in `CL-000000001` format.
- Added `organizations.organization_code` in `ORG-000000001` format.
- Backfilled existing rows through PostgreSQL sequences.
- Added `NOT NULL`, unique constraints, format checks, and lookup indexes.
- Updated backend organization/client lookup DTOs to include `organizationCode` and `clientNumber`.
- Updated `CaseDetailResponse` with organization/client display names and business identifiers for frontend display.
- Updated React lookup dropdowns and case detail overview to show names plus business identifiers instead of raw client/organization UUIDs.

#### Rationale

UUIDs remain the write-safe internal identifiers, but normal workflows now have stable human-readable references suitable for support, audit discussions, and operational screens.

---

## 2026-05-23
### Brooklyn Insurance & Robotics Demo Seed Tool

#### Milestone
Added a Python-based synthetic enterprise demo data generator for the fictional Brooklyn Insurance & Robotics operating theme.

#### Implementation Details

- Created `tools/seed_bir_demo.py`.
- Created `tools/README.md` with setup, dry-run, reset, and scale configuration instructions.
- Uses Faker for NYC-centric synthetic data and psycopg PostgreSQL `COPY` for bulk inserts.
- Default scale generates 250 organizations, 25,000 clients, 75,000 cases, 150,000 notes, 100,000 tasks, and 50,000 attachment metadata rows.
- Demo rows are resettable through explicit BIR markers on organization/client external IDs and case numbers.
- The tool does not use an ORM and does not alter schema.

#### Validation

Local full-scale run completed in 22.66 seconds and validated expected row counts for all generated tables.

---

## 2026-05-23
### Frontend UI Modernized

#### Milestone
Refactored the React frontend into a denser, professional enterprise case management interface while preserving existing backend API contracts.

#### Implementation Details

- Modernized the application shell with a richer sidebar, workspace context, sticky header, and compact authenticated user controls.
- Reworked the dashboard into production-style summary cards and workflow action surfaces.
- Added a professional case queue toolbar with current-page search and status filtering.
- Rebuilt case list rows to emphasize case number, title, status, priority, type, assignee state, due date, and created date.
- Refactored the case detail screen with a stronger case header, compact metrics, polished tabs, denser overview fields, activity-style notes, task cards, and cleaner file presentation.
- Removed visible UUID leakage from normal read-only UI by showing user-facing actor/assignee labels instead of raw identifiers.
- Replaced dated mojibake UI text with clean ASCII copy in touched frontend files.
- Rebuilt responsive CSS for desktop and smaller screens without changing backend endpoints.

#### Validation

Frontend production build passed with `npm run build`.

---

## 2026-05-23
### Dashboard Operational Widgets and Metric Audit

#### Milestone
Reworked the dashboard into a backend-driven service console with corrected closure metrics and real operational widgets.

#### Implementation Details

- Audited dashboard metrics and corrected `closedToday` to include both `closed_at` and `resolved_at` transitions during the current UTC day.
- Added `GET /api/dashboard/overview` returning metrics plus recent assigned cases, escalation watch, overdue queue, and recent activity.
- Added repository queries for latest assigned cases, latest escalated cases, top overdue cases, recent notes, recent status history, and recent task updates.
- Replaced dashboard lower panels with clickable operational widgets populated from backend data.
- Tightened remaining UI density across the dashboard, sidebar, global search, KPI cards, widget rows, and hover states.
- Strengthened dashboard integration tests to assert metric deltas for assigned-to-me, overdue, open, escalated, and closed/resolved-today behavior.

#### Validation

- Backend tests passed with `.\mvnw.cmd test` (`66` tests, `0` failures).
- Frontend production build passed with `npm run build`.

---

## 2026-05-23
### Enterprise Console Density and Live Reporting

#### Milestone
Added live dashboard reporting and server-side case list filtering while tightening the frontend into a denser operations-console experience.

#### Implementation Details

- Added `GET /api/dashboard/metrics` with total, open, assigned-to-me, overdue, escalated, and closed-today case counts.
- Added backend case list filtering for `q`, `status`, `priority`, and `type` on `GET /api/cases`.
- Wired the dashboard to live metrics and removed placeholder dashboard copy.
- Wired the case list toolbar to server-side search and filters with 50-row pagination controls.
- Tightened frontend density globally: smaller gutters, compact cards, denser tables, tighter record highlights, reduced empty states, compact tabs, richer activity timeline, and stronger task work item presentation.
- Updated `docs/API_CONTRACT.md` for dashboard metrics and case list query parameters.

#### Validation

- Backend tests passed with `.\mvnw.cmd test` (`65` tests, `0` failures).
- Frontend production build passed with `npm run build`.
Local Vite dev server responded with HTTP 200 at `http://127.0.0.1:5173`.

---

## 2026-05-23
### Salesforce-Inspired Frontend Restyle

#### Milestone
Restyled the React frontend toward a Salesforce Lightning-style enterprise CRM and case management workspace without changing backend APIs or adding a heavy UI framework.

#### Implementation Details

- Replaced the dark-heavy shell with a light global header, workspace selector feel, global search surface, compact left object navigation, and right-aligned user controls.
- Reworked the dashboard as an operations home page with CRM-style cards, work shortcuts, service control sections, and placeholder metric cards where backend reporting APIs do not exist.
- Restyled the cases page as an object list view with object header, list view title, search/filter toolbar, dense rows, status pills, priority pills, and a compact `New` action.
- Reworked the case detail page into a record page with highlights panel, case object icon, key fields, quick actions, compact tabs, details section, activity timeline, task related-list cards, and files related list.
- Preserved login, case list, case creation, case detail, notes, tasks, attachments, and status transition behavior.

#### Validation

Frontend production build passed with `npm run build`.

---

## 2026-05-23
### Phase 8.6 - Enterprise Data Grid Polish

#### Milestone
Refactored CaseAxis table UX to enterprise CRM data grid standards (Salesforce / ServiceNow style).

#### Implementation Details

- Added smooth `transition: background 0.1s` to all table rows for polished hover response.
- Added zebra striping (`#fafbfc` on even rows) for improved large-dataset scanability.
- Implemented sticky table headers via `position: sticky; top: 0; z-index: 2` with `overflow-y: auto; max-height: 70vh` on `.table-wrapper`, making all tables self-contained scroll containers.
- Changed `.list-view-card` and `.detail-card` from `overflow: hidden` to `overflow: clip` to unblock sticky header behavior without breaking rounded corner clipping.
- Strengthened column headers: bolder font weight, tighter `letter-spacing: 0.06em`, `2px` bottom border for stronger visual separation.
- Added `.row-selected` CSS class with left border accent and subtle blue highlight for future selection state wiring.
- Normalized all status/priority/task badges to a fixed `height: 20px`, `padding: 0 7px`, `border-radius: 3px` for consistent enterprise badge sizing across the UI.
- Improved pagination controls: record count on left, page info centered between navigation buttons, arrow symbol labels (`«` `‹` `›` `»`) with `title` attributes for accessibility.
- Added `.pagination-page-info` and `.pagination-summary` as distinct styled elements for a cleaner CRM-grade pagination strip.
- Replaced all generic `.empty-state` text in table/feed contexts with structured `.empty-state-panel` components (title + body) using professional enterprise copy:
  - Case list: "No cases found" / filter guidance
  - Activity tab: "No activity recorded" / note guidance
  - Tasks tab: "No tasks assigned" / task workflow guidance
  - Files tab: "No files attached" / attachment API guidance

#### Validation

Frontend production build passed with `npm run build`.

---

## 2026-05-23
### Phase 9 — Client & Organization CRM Module

#### Milestone
Transformed CaseAxis from a case tracker into a multi-entity CRM operations platform with full Client and Organization list/detail screens, paginated search, and per-entity case metrics.

#### Backend Changes

**New endpoints (7 total):**

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/organizations` | Paginated org list (search, active filter) |
| `GET` | `/api/organizations/{id}` | Org detail with 5 case metrics |
| `GET` | `/api/organizations/{id}/clients` | Paginated clients for org |
| `GET` | `/api/organizations/{id}/cases` | Paginated cases for org |
| `GET` | `/api/clients` | Paginated client list (search, org filter, active filter) |
| `GET` | `/api/clients/{id}` | Client detail with 4 case metrics |
| `GET` | `/api/clients/{id}/cases` | Paginated cases for client |

**Key design decisions:**

- `OrganizationSummary` extended with `clientCount`, `caseCount`, `openCaseCount`, `active`, `createdAt`.
- `ClientSummary` extended with `email`, `phone`, `organizationCode`, `organizationName`, `active`, `createdAt`.
- New `OrganizationDetailResponse` and `ClientDetailResponse` records for detail endpoints.
- Split repository queries (`filterActive` / `searchActive`) to avoid `lower(bytea)` null type inference bug in PostgreSQL when LOWER() is applied to a null JDBC parameter.
- Batch org lookup in `ClientService.listClients()` to avoid N+1: collect unique orgIds, one `findAllById` call, O(1) Map lookup per client.
- `CaseRepository` extended with 10 new count/find queries scoped to client and organization.

**Test changes:**
- `OrganizationControllerTest` updated: paginated assertions (`$.data.content`), 6 new tests for detail and related-resource endpoints.
- `ClientControllerTest` updated: paginated assertions, 6 new tests. Tests that insert specific named records now use `?q=<unique>` search filters and unique names (e.g., "Zxqtest") to avoid BIR seed data flooding page 0 of alphabetically sorted results.

#### Frontend Changes

- **`types/api.ts`**: Added `OrganizationDetail`, `ClientDetail`; expanded `OrganizationSummary` and `ClientSummary` with new fields.
- **`lib/apiClient.ts`**: `organizations.list()` and `clients.list()` now return `Page<T>` with params; added `.get()`, `.clients()`, `.cases()` methods for both.
- **`components/AppShell.tsx`**: Added Clients, Organizations, Tasks (placeholder), Reports (placeholder) nav entries.
- **`App.tsx`**: Added routes `/clients`, `/clients/:id`, `/organizations`, `/organizations/:id`, `/tasks` (placeholder), `/reports` (placeholder).
- **`pages/CreateCasePage.tsx`**: Updated to extract `.content` from paginated list responses; uses `size=500` for dropdown population.
- **`pages/ClientListPage.tsx`** (new): Server-side search, active filter, dense CRM grid, pagination.
- **`pages/ClientDetailPage.tsx`** (new): Record page with highlights panel, case metrics, tabs (Overview, Cases).
- **`pages/OrgListPage.tsx`** (new): Server-side search, active filter, org grid with client/case counts.
- **`pages/OrgDetailPage.tsx`** (new): Record page with 5 metrics, tabs (Overview, Clients, Cases).

#### Validation

- Backend: `78` tests, `0` failures.
- Frontend: `npm run build` passed.

---

## 2026-05-23
### Phase 8.5 - Frontend Typography Hierarchy Refactor

#### Milestone
Centralized the CaseAxis frontend typography system and strengthened hierarchy across the enterprise console UI.

#### Implementation Details

- Added global font size tokens from `--font-xs` through `--font-2xl` in `frontend/src/index.css`.
- Added global font weight tokens for regular, medium, semibold, and bold usage.
- Standardized the frontend font stack to `Inter, Segoe UI, system-ui, sans-serif`.
- Reworked app shell typography for stronger CaseAxis branding, muted workspace context, compact search text, and medium-weight user controls.
- Tuned dashboard typography so page headings, KPI labels, KPI values, widget headers, helper text, and timestamps have distinct enterprise hierarchy.
- Tightened case list typography with uppercase table headers, semibold primary case titles, muted case numbers, and smaller secondary metadata.
- Refined case detail typography for prominent record titles, compact metadata labels, semibold values, consistent tabs, and clearer timeline hierarchy.
- Normalized navigation, badges, buttons, forms, modal, and login typography around shared tokens instead of ad hoc values.
- Preserved existing layouts, frontend behavior, backend APIs, and API call structure.

#### Validation

Frontend production build passed with `npm run build`.
