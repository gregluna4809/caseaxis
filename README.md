# CaseAxis

A full-stack case management system for operational casework, built around a 14-table relational schema, JWT-secured REST API, and a React analytics console. Designed to model the kind of high-volume, multi-role workload typical of regulatory, insurance, and government case operations.

The included seed tooling populates the system with a realistic Brooklyn-area "Risk & Robotics" insurance domain — 250 organizations, 25,000 clients, 75,000+ cases, 100,000 tasks, and 150,000 notes — so the application can be evaluated under representative scale rather than against an empty database.

---

## Tech Stack

**Backend**
- Java 21, Spring Boot 3.4
- Spring Security with JWT authentication
- Spring Data JPA / Hibernate 6
- PostgreSQL 16
- Flyway for versioned database migrations
- Maven

**Frontend**
- React 18 with TypeScript
- Vite
- React Router
- Context API for auth state

**Infrastructure & Tooling**
- Docker Compose for local orchestration
- GitHub Actions for CI
- Testcontainers for integration tests
- JUnit 5, Mockito, AssertJ
- Python + Faker for synthetic demo data generation

---

## Features

**Case operations**
- Full case lifecycle: New → Assigned → In Review → Pending Info → Escalated → Approved / Denied / Closed → Reopened
- Server-side search, filtering, and pagination across 75k+ records
- Case assignment, status history, notes, tasks, and attachment metadata
- Priority and type taxonomies with seeded lookup data

**CRM**
- 25,000 client records with organization affiliations
- 250 organizations with derived case counts and open-case aggregates
- Server-side list views with status filters and search by name, code, or email

**Tasks**
- 100,000 task records linked to parent cases
- Overdue detection with visual highlighting
- Filterable by status, assignee, or overdue-only

**Analytics**
- Aggregate reporting endpoints with filterable date range, organization, client, type, status, and assignee
- Distribution charts for case status and case type
- Overdue aging buckets (1-7d / 8-30d / 31-90d / 90+d)
- Assignee and organization workload breakdowns
- CSV and JSON export

**Security & operations**
- JWT-based authentication with role-based authorization (ADMIN role bootstrapped on first run)
- Audit log table for tracking entity changes
- Flyway-managed schema with 14 ordered migrations

---

## Screenshots

| | |
|---|---|
| **Operations Dashboard** | **Case List View (75k records)** |
| ![Dashboard](docs/screenshots/dashboard.png) | ![Cases](docs/screenshots/cases.png) |
| **Clients (25k records)** | **Organizations (250 records)** |
| ![Clients](docs/screenshots/clients.png) | ![Organizations](docs/screenshots/organizations.png) |
| **Task Workspace (100k records)** | **Analytics Reports** |
| ![Tasks](docs/screenshots/tasks.png) | ![Reports](docs/screenshots/reports.png) |

---

## Quick Start

### Prerequisites
- Docker Desktop
- Python 3.10+ (for the demo seed script, optional)
- Node.js 20+ (for the frontend dev server)

### 1. Clone and configure

```bash
git clone https://github.com/<your-username>/CaseAxis.git
cd CaseAxis
cp .env.example .env
```

The default `.env` values work out of the box for local development. Edit only if you have a port conflict.

### 2. Start the backend stack

```bash
docker compose up --build
```

This starts PostgreSQL 16 on host port 5434 and the Spring Boot backend on port 8080. Flyway applies the 14 schema migrations on first startup; subsequent starts are incremental. An `admin` / `admin` user is bootstrapped automatically when the user table is empty.

Watch for:
```
Successfully applied 14 migrations to schema "public"
Started CaseAxisApplication in X.X seconds
```

### 3. Start the frontend

In a separate terminal:

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173 and sign in with `admin` / `admin`.

### 4. (Optional) Load demo data

The system is functional with an empty database, but the full UX — populated dashboards, paginated lists, populated analytics — only shows once seed data is loaded.

```bash
python -m pip install Faker "psycopg[binary]"
export DB_URL="postgresql://caseaxis:caseaxis_dev@localhost:5434/caseaxis"
python tools/seed_bir_demo.py --reset-demo
```

Defaults: 250 orgs, 25k clients, 75k cases, 150k notes, 100k tasks, 50k attachments. Scale up with `--cases 500000` if desired. Allow ~5 minutes at default scale; longer for the full 500k run.

---

## Project Structure

```
CaseAxis/
├── backend/                          Spring Boot service
│   ├── src/main/java/com/caseaxis/
│   │   ├── auth/                     JWT, user details, admin bootstrap
│   │   ├── cases/                    Case domain, controllers, services
│   │   ├── clients/                  Client CRM
│   │   ├── organizations/            Organization aggregates
│   │   ├── tasks/                    Case tasks
│   │   ├── reports/                  Analytics endpoints
│   │   └── security/                 Filters, configuration
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/             14 Flyway migrations (V1–V14)
│   └── src/test/java/                Unit + integration tests
├── frontend/                         React + Vite + TypeScript app
├── tools/
│   ├── seed_bir_demo.py              Synthetic data generator (Faker + psycopg)
│   └── README.md
├── docs/
│   ├── DATABASE_DESIGN.md
│   └── screenshots/
├── docker-compose.yml
├── .github/workflows/ci.yml          Backend + frontend CI
└── .env.example
```

---

## Database Schema

Fourteen migrations build the schema in order:

| Migration | Purpose |
|---|---|
| V1 | Trigger functions for timestamps and audit |
| V2 | Users |
| V3 | Roles and permissions |
| V4 | User-role and role-permission joins |
| V5 | Lookup tables (statuses, priorities, types) |
| V6 | Seed lookup data |
| V7 | Case number sequence |
| V8 | Organizations and clients |
| V9 | Cases |
| V10 | Case status history and assignments |
| V11 | Case tasks, notes, and attachments |
| V12 | Audit logs |
| V13 | Supplementary indexes |
| V14 | Client and organization business identifiers |

See [docs/DATABASE_DESIGN.md](docs/DATABASE_DESIGN.md) for the full data model.

---

## Testing

```bash
cd backend
mvn verify
```

Integration tests use [Testcontainers](https://www.testcontainers.org/) to spin up a real PostgreSQL 16 container, so they exercise the production database engine rather than an in-memory substitute. Requires Docker.

Frontend:

```bash
cd frontend
npm test
```

---

## Configuration

| Variable | Default | Notes |
|---|---|---|
| `POSTGRES_USER` | `caseaxis` | DB superuser created on first run |
| `POSTGRES_PASSWORD` | `caseaxis_dev` | Local-dev value; replace for any non-local deployment |
| `POSTGRES_DB` | `caseaxis` | Database name |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://db:5432/caseaxis` | Hostname is the docker-compose service name |
| `SPRING_DATASOURCE_USERNAME` | `caseaxis` | Must match `POSTGRES_USER` |
| `SPRING_DATASOURCE_PASSWORD` | `caseaxis_dev` | Must match `POSTGRES_PASSWORD` |
| `SPRING_FLYWAY_LOCATIONS` | `classpath:db/migration` | Override only for alternate migration sources |

All values live in `.env`. The file is gitignored; `.env.example` documents the schema.

---

## Development Notes

This project was developed with AI-assisted tooling (Claude, Codex) for implementation acceleration and code generation. Architectural decisions, debugging, integration, deployment, and verification were directed by the author. The development workflow followed a scan-driven improvement loop: a static analysis pass identified operational gaps (missing CI, no Dockerfile, no integration tests, no `.env.example`), each gap was addressed with a targeted prompt, and the result was verified end-to-end by running the stack locally with seeded data.

---

## License

MIT
