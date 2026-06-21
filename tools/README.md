# CaseAxis Tools

## Production Smoke Test

`production_smoke_test.py` validates a deployed CaseAxis environment without storing credentials. It checks:

- `/actuator/health`
- authentication
- case list loading
- case detail loading
- creation of a clearly labeled smoke-test note
- audit trail recording for the note action

The script returns exit code `0` on success and nonzero on failure. Passwords are never logged or written to files.

### Interactive Mode

Prompts for username and password. The password prompt uses Python `getpass`, so the password is not echoed.

```powershell
python tools\production_smoke_test.py --base-url http://localhost:8080
```

### Environment Variable Mode

Useful for automated execution. `CASEAXIS_PASSWORD` is read from the process environment only.

```powershell
$env:CASEAXIS_USERNAME = "admin"
$env:CASEAXIS_PASSWORD = "<password>"
$env:CASEAXIS_BASE_URL = "http://localhost:8080"
python tools\production_smoke_test.py
```

### Production Execution

```powershell
$env:CASEAXIS_USERNAME = "<production-user>"
$env:CASEAXIS_PASSWORD = "<production-password>"
python tools\production_smoke_test.py --base-url https://caseaxis.pulse-forge.com
```

Do not commit credentials, paste them into scripts, or write them to `.env` files intended for source control.

---

## Human-Services Seed Data

`seed_bir_demo.py` populates CaseAxis with NYC-centric synthetic human-services demo data for benefits review, recipient support, appeals, documentation, and determinations workflows. The file name and row prefixes remain stable for existing automation, but the generated values are domain-native and use `mbra-demo.example` email addresses.

The default scale is `small` for fast local verification:

- 40 organizations
- 1,000 clients
- 2,200 cases
- 4,400 notes
- 3,300 tasks
- 1,650 attachment metadata records

Full scale remains available with `--scale full`:

- 250 organizations
- 25,000 clients
- 75,000 cases
- 150,000 notes
- 100,000 tasks
- 50,000 attachment metadata records

## Requirements

Install Python dependencies:

```powershell
python -m pip install Faker psycopg[binary]
```

The script connects with `DB_URL` if set, otherwise it uses:

```text
postgresql://greg:mypass123@localhost:5434/caseaxis_local
```

You can also pass discrete connection fields with `--db-host`, `--db-port`, `--db-name`, `--db-user`, and `--db-password`, or their matching `POSTGRES_*` / `DB_*` environment variables. The resolved database target is logged at startup.

## Dry Run

Generate the configured data in memory without writing to the database:

```powershell
python tools\seed_bir_demo.py --dry-run
```

Use smaller counts for fast local checks:

```powershell
python tools\seed_bir_demo.py --dry-run --organizations 5 --clients 50 --cases 100 --notes 200 --tasks 150 --attachments 75
```

## Insert Demo Data

```powershell
python tools\seed_bir_demo.py --reset-demo --scale small
```

`--reset-demo` removes prior demo rows before inserting new rows. Demo rows are identified by:

- `organizations.external_id LIKE 'BIR-DEMO-ORG-%'`
- `clients.external_id LIKE 'BIR-DEMO-CLIENT-%'`
- `cases.case_number LIKE 'BIR-%'`

Dependent notes, tasks, attachments, and assignments for those demo cases are removed first.

The reset also removes generated reviewer users whose usernames match `mbra-reviewer-%`.

The seeding session sets a short PostgreSQL `lock_timeout` and a bounded `statement_timeout`. If the backend or another client holds conflicting locks, the script fails fast with an actionable message instead of hanging. Stop the backend first for normal reloads:

```powershell
docker compose stop backend
python tools\seed_bir_demo.py --reset-demo --scale small
```

For local-only workflows where it is acceptable to disconnect other database clients, use:

```powershell
python tools\seed_bir_demo.py --reset-demo --scale small --terminate-sessions
```

## Reset Only

```powershell
python tools\seed_bir_demo.py --reset-only
```

## Configurable Scale

```powershell
python tools\seed_bir_demo.py --reset-demo --scale full
```

Individual counts can still override either preset:

```powershell
python tools\seed_bir_demo.py `
  --scale small `
  --organizations 10 `
  --clients 250 `
  --cases 500 `
  --notes 1000 `
  --tasks 750 `
  --attachments 300 `
  --reset-demo
```

## Validation

After inserts or reset-only runs, the script logs validation counts for:

- organizations
- clients
- cases
- notes
- tasks
- attachments
- reviewer/staff users

After inserts, it also prints a data check with distinct organization names, distinct assignee names, assignee distribution, status distribution, and the unassigned case share.

The script uses PostgreSQL `COPY` through `psycopg` and does not use an ORM or alter the schema.
