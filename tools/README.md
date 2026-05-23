# CaseAxis Demo Data Tools

## Brooklyn Insurance & Robotics Seed Data

`seed_bir_demo.py` populates CaseAxis with NYC-centric synthetic enterprise demo data for the fictional Brooklyn Insurance & Robotics theme.

Default scale:

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
postgresql://caseaxis:caseaxis@localhost:5432/caseaxis
```

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
python tools\seed_bir_demo.py --reset-demo
```

`--reset-demo` removes prior BIR demo rows before inserting new rows. Demo rows are identified by:

- `organizations.external_id LIKE 'BIR-DEMO-ORG-%'`
- `clients.external_id LIKE 'BIR-DEMO-CLIENT-%'`
- `cases.case_number LIKE 'BIR-%'`

Dependent notes, tasks, attachments, and assignments for those demo cases are removed first.

## Reset Only

```powershell
python tools\seed_bir_demo.py --reset-only
```

## Configurable Scale

```powershell
python tools\seed_bir_demo.py `
  --organizations 250 `
  --clients 25000 `
  --cases 75000 `
  --notes 150000 `
  --tasks 100000 `
  --attachments 50000 `
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

The script uses PostgreSQL `COPY` through `psycopg` and does not use an ORM or alter the schema.
