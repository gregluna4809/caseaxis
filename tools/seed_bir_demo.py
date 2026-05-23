#!/usr/bin/env python3
"""
Generate Brooklyn Insurance & Robotics synthetic demo data for CaseAxis.

The script uses direct SQL and PostgreSQL COPY via psycopg. It intentionally
does not use an ORM and does not alter schema.
"""

from __future__ import annotations

import argparse
import csv
import io
import logging
import os
import random
import sys
import time
import uuid
from dataclasses import dataclass
from datetime import date, datetime, timedelta, timezone
from typing import Iterable

try:
    import psycopg
    from faker import Faker
except ImportError as exc:
    print(
        "Missing dependency. Install with: python -m pip install Faker psycopg[binary]",
        file=sys.stderr,
    )
    raise SystemExit(2) from exc


LOGGER = logging.getLogger("seed_bir_demo")
DEMO_CASE_PREFIX = "BIR-"
DEMO_EXTERNAL_PREFIX = "BIR-DEMO"
DEFAULT_DB_URL = "postgresql://caseaxis:caseaxis@localhost:5432/caseaxis"

ORG_EXAMPLES = [
    "Brooklyn Insurance & Robotics",
    "Atlantic Risk Systems",
    "Metro Autonomous Claims",
    "Harbor Industrial Assurance",
    "Kings County Robotics Liability",
]

NYC_AREAS = [
    ("Brooklyn", "NY", ["11201", "11205", "11206", "11211", "11215", "11217", "11220", "11221", "11225", "11231", "11233", "11238"], 58),
    ("Queens", "NY", ["11101", "11354", "11368", "11372", "11375", "11432", "11435", "11691"], 18),
    ("Bronx", "NY", ["10451", "10452", "10453", "10457", "10458", "10461", "10467"], 10),
    ("New York", "NY", ["10001", "10002", "10003", "10011", "10019", "10027", "10029"], 8),
    ("Staten Island", "NY", ["10301", "10304", "10306", "10309", "10314"], 3),
    ("Jersey City", "NJ", ["07302", "07304", "07305", "07306"], 2),
    ("Hoboken", "NJ", ["07030"], 1),
]

CASE_SCENARIOS = [
    ("COMPLAINT", "Robotics liability complaint", "Client alleges property damage involving autonomous warehouse equipment."),
    ("COMPLAINT", "Insurance coverage complaint", "Dispute over policy coverage determination after reported loss."),
    ("APPLICATION", "Commercial underwriting review", "Underwriting review for mixed insurance and robotics operations coverage."),
    ("APPLICATION", "Benefits eligibility review", "Benefits documentation submitted for review and eligibility determination."),
    ("INQUIRY", "Policy clarification inquiry", "Client requested clarification regarding exclusions and robotics endorsements."),
    ("INVESTIGATION", "Autonomous equipment incident investigation", "Incident review opened for reported autonomous equipment malfunction."),
    ("INVESTIGATION", "Fraud review referral", "Claim referred for fraud review due to inconsistent documentation."),
    ("GENERAL", "Compliance follow-up", "Follow-up required for compliance documentation and operational controls."),
]

NOTE_BODIES = [
    "Initial intake completed. Supporting documents reviewed for completeness.",
    "Client contacted by phone; voicemail left with callback instructions.",
    "Policy documents compared against submitted incident timeline.",
    "Robotics incident narrative reviewed with claims specialist.",
    "Additional photographs requested from claimant.",
    "Supervisor review requested due to potential coverage complexity.",
    "External party response received and attached to case record.",
    "Follow-up scheduled after receipt of missing documentation.",
    "Potential duplicate claim checked against prior case activity.",
    "Compliance checklist updated based on available evidence.",
]

TASK_TITLES = [
    "Request missing documentation",
    "Review policy endorsement",
    "Contact claimant",
    "Validate incident timeline",
    "Prepare supervisor summary",
    "Check fraud indicators",
    "Confirm robotics asset serial number",
    "Review compliance checklist",
    "Draft determination notes",
    "Schedule follow-up call",
]

ATTACHMENT_TYPES = [
    ("incident-photo", "image/jpeg", "Incident photo supplied by client"),
    ("policy-declaration", "application/pdf", "Policy declaration page"),
    ("robotics-log", "text/csv", "Robotics telemetry export"),
    ("claim-form", "application/pdf", "Signed claim form"),
    ("inspection-report", "application/pdf", "Field inspection report"),
    ("email-thread", "message/rfc822", "Claim communication export"),
]

STATUS_WEIGHTS = {
    "NEW": 6,
    "ASSIGNED": 14,
    "IN_REVIEW": 25,
    "PENDING_INFO": 17,
    "ESCALATED": 7,
    "APPROVED": 12,
    "DENIED": 8,
    "CLOSED": 9,
    "REOPENED": 2,
}

PRIORITY_WEIGHTS = {"LOW": 18, "MEDIUM": 52, "HIGH": 24, "CRITICAL": 6}
TASK_STATUS_WEIGHTS = {"PENDING": 35, "IN_PROGRESS": 30, "COMPLETED": 28, "CANCELLED": 7}
AREA_CODES_BY_STATE = {
    "NY": ["212", "315", "347", "516", "518", "585", "607", "631", "646", "718", "845", "914", "917", "929"],
    "NJ": ["201", "551", "609", "732", "848", "856", "862", "908", "973"],
}


@dataclass(frozen=True)
class Scale:
    organizations: int
    clients: int
    cases: int
    notes: int
    tasks: int
    attachments: int


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Seed CaseAxis with BIR enterprise demo data.")
    parser.add_argument("--db-url", default=os.getenv("DB_URL", DEFAULT_DB_URL))
    parser.add_argument("--organizations", type=int, default=250)
    parser.add_argument("--clients", type=int, default=25_000)
    parser.add_argument("--cases", type=int, default=75_000)
    parser.add_argument("--notes", type=int, default=150_000)
    parser.add_argument("--tasks", type=int, default=100_000)
    parser.add_argument("--attachments", type=int, default=50_000)
    parser.add_argument("--seed", type=int, default=4809)
    parser.add_argument("--dry-run", action="store_true", help="Generate and validate in memory without writing.")
    parser.add_argument("--reset-demo", action="store_true", help="Delete previously seeded BIR demo rows before inserting.")
    parser.add_argument("--reset-only", action="store_true", help="Delete BIR demo rows and exit.")
    parser.add_argument("--batch-size", type=int, default=10_000)
    parser.add_argument("--log-level", default="INFO", choices=["DEBUG", "INFO", "WARNING", "ERROR"])
    return parser.parse_args()


def weighted_choice(mapping: dict[str, int]) -> str:
    return random.choices(list(mapping.keys()), weights=list(mapping.values()), k=1)[0]


def weighted_area() -> tuple[str, str, str]:
    area = random.choices(NYC_AREAS, weights=[item[3] for item in NYC_AREAS], k=1)[0]
    return area[0], area[1], random.choice(area[2])


def realistic_us_phone(state: str, include_extension: bool = False) -> str:
    area_code = random.choice(AREA_CODES_BY_STATE.get(state, ["212"]))
    exchange = random.randint(200, 999)
    line = random.randint(0, 9999)
    phone = f"({area_code}) {exchange:03d}-{line:04d}"
    if include_extension and random.random() < 0.03:
        phone = f"{phone} ext. {random.randint(100, 999)}"
    return phone


def copy_rows(
    conn: psycopg.Connection,
    table: str,
    columns: list[str],
    rows: Iterable[tuple],
    label: str,
    batch_size: int,
) -> int:
    count = 0
    with conn.cursor() as cur:
        sql = f"COPY {table} ({', '.join(columns)}) FROM STDIN WITH (FORMAT CSV, NULL '')"
        with cur.copy(sql) as copy:
            buffer = io.StringIO()
            writer = csv.writer(buffer, lineterminator="\n")
            for row in rows:
                writer.writerow(row)
                count += 1
                if count % batch_size == 0:
                    copy.write(buffer.getvalue())
                    buffer.seek(0)
                    buffer.truncate(0)
                    LOGGER.info("copied %s rows into %s", f"{count:,}", label)
            if buffer.tell():
                copy.write(buffer.getvalue())
    LOGGER.info("finished %s: %s rows", label, f"{count:,}")
    return count


def load_reference_data(conn: psycopg.Connection) -> dict[str, dict[str, str] | list[str] | str]:
    with conn.cursor() as cur:
        cur.execute("SELECT id FROM users WHERE username = 'admin' AND is_deleted = FALSE")
        admin = cur.fetchone()
        if not admin:
            raise RuntimeError("Admin user not found; run application migrations/bootstrap first.")

        refs: dict[str, dict[str, str] | list[str] | str] = {"admin_id": str(admin[0])}
        for table, key in [
            ("case_statuses", "statuses"),
            ("case_priorities", "priorities"),
            ("case_types", "types"),
            ("task_statuses", "task_statuses"),
        ]:
            cur.execute(f"SELECT code, id FROM {table} WHERE is_active = TRUE")
            refs[key] = {code: str(row_id) for code, row_id in cur.fetchall()}

        cur.execute("SELECT id FROM users WHERE is_active = TRUE AND is_deleted = FALSE")
        refs["user_ids"] = [str(row[0]) for row in cur.fetchall()]
    return refs


def reset_demo(conn: psycopg.Connection) -> dict[str, int]:
    LOGGER.info("resetting existing BIR demo rows")
    statements = [
        ("attachments", "DELETE FROM case_attachments WHERE case_id IN (SELECT id FROM cases WHERE case_number LIKE %s)", (f"{DEMO_CASE_PREFIX}%",)),
        ("notes", "DELETE FROM case_notes WHERE case_id IN (SELECT id FROM cases WHERE case_number LIKE %s)", (f"{DEMO_CASE_PREFIX}%",)),
        ("tasks", "DELETE FROM case_tasks WHERE case_id IN (SELECT id FROM cases WHERE case_number LIKE %s)", (f"{DEMO_CASE_PREFIX}%",)),
        ("assignments", "DELETE FROM case_assignments WHERE case_id IN (SELECT id FROM cases WHERE case_number LIKE %s)", (f"{DEMO_CASE_PREFIX}%",)),
        ("cases", "DELETE FROM cases WHERE case_number LIKE %s", (f"{DEMO_CASE_PREFIX}%",)),
        ("clients", "DELETE FROM clients WHERE external_id LIKE %s", (f"{DEMO_EXTERNAL_PREFIX}-CLIENT-%",)),
        ("organizations", "DELETE FROM organizations WHERE external_id LIKE %s", (f"{DEMO_EXTERNAL_PREFIX}-ORG-%",)),
    ]
    deleted: dict[str, int] = {}
    with conn.cursor() as cur:
        for label, sql, params in statements:
            cur.execute(sql, params)
            deleted[label] = cur.rowcount
            LOGGER.info("deleted %s %s rows", f"{cur.rowcount:,}", label)
    return deleted


def organization_rows(fake: Faker, scale: Scale, admin_id: str) -> tuple[list[str], list[tuple]]:
    columns = [
        "id", "name", "external_id", "address_line1", "city", "state_province", "postal_code",
        "country", "phone", "email", "notes", "is_active", "is_deleted", "created_at", "updated_at", "created_by",
    ]
    ids: list[str] = []
    rows: list[tuple] = []
    names = ORG_EXAMPLES + [
        f"{borough} {suffix}"
        for borough in ["Brooklyn", "Queens", "Bronx", "Manhattan", "Harbor", "Atlantic", "Metro", "Hudson", "Kings County"]
        for suffix in ["Risk Partners", "Claims Group", "Robotics Assurance", "Industrial Mutual", "Coverage Services"]
    ]
    for i in range(scale.organizations):
        org_id = str(uuid.uuid4())
        ids.append(org_id)
        city, state, postal = weighted_area()
        name = names[i] if i < len(names) else f"{fake.company()} Risk & Robotics"
        created_at = fake.date_time_between(start_date="-4y", end_date="-30d", tzinfo=timezone.utc).isoformat()
        rows.append((
            org_id, name[:255], f"{DEMO_EXTERNAL_PREFIX}-ORG-{i + 1:06d}", fake.street_address(), city, state,
            postal, "USA", realistic_us_phone(state, include_extension=True), f"ops-{i + 1}@bir-demo.example",
            "Synthetic BIR demo organization", True, False, created_at, created_at, admin_id,
        ))
    return ids, rows


def client_rows(fake: Faker, scale: Scale, admin_id: str, org_ids: list[str]) -> tuple[list[str], list[tuple]]:
    columns = [
        "id", "organization_id", "first_name", "last_name", "middle_name", "date_of_birth", "email", "phone",
        "address_line1", "city", "state_province", "postal_code", "country", "external_id", "notes",
        "is_active", "is_deleted", "created_at", "updated_at", "created_by",
    ]
    ids: list[str] = []
    rows: list[tuple] = []
    for i in range(scale.clients):
        client_id = str(uuid.uuid4())
        ids.append(client_id)
        city, state, postal = weighted_area()
        first = fake.first_name()
        last = fake.last_name()
        org_id = random.choice(org_ids) if random.random() < 0.36 else ""
        created_at = fake.date_time_between(start_date="-4y", end_date="-7d", tzinfo=timezone.utc).isoformat()
        rows.append((
            client_id, org_id, first, last, fake.first_name() if random.random() < 0.18 else "",
            fake.date_of_birth(minimum_age=18, maximum_age=88).isoformat(),
            f"{first}.{last}.{i + 1}@bir-demo.example".lower().replace("'", ""),
            realistic_us_phone(state), fake.street_address(), city, state, postal, "USA",
            f"{DEMO_EXTERNAL_PREFIX}-CLIENT-{i + 1:08d}", "Synthetic BIR demo client",
            True, False, created_at, created_at, admin_id,
        ))
    return ids, rows


def case_rows(fake: Faker, scale: Scale, refs: dict, org_ids: list[str], client_ids: list[str]) -> tuple[list[str], list[tuple]]:
    columns = [
        "id", "case_number", "title", "description", "status_id", "priority_id", "type_id", "organization_id",
        "client_id", "assigned_to_id", "assigned_at", "due_date", "resolved_at", "closed_at", "reopened_count",
        "is_deleted", "created_at", "updated_at", "created_by", "updated_by",
    ]
    ids: list[str] = []
    rows: list[tuple] = []
    admin_id = refs["admin_id"]
    user_ids = refs["user_ids"] or [admin_id]
    statuses = refs["statuses"]
    priorities = refs["priorities"]
    types = refs["types"]
    for i in range(scale.cases):
        case_id = str(uuid.uuid4())
        ids.append(case_id)
        type_code, title_base, description = random.choice(CASE_SCENARIOS)
        status_code = weighted_choice(STATUS_WEIGHTS)
        priority_code = weighted_choice(PRIORITY_WEIGHTS)
        created_dt = fake.date_time_between(start_date="-30M", end_date="-1d", tzinfo=timezone.utc)
        updated_dt = fake.date_time_between(start_date=created_dt, end_date="now", tzinfo=timezone.utc)
        client_id = random.choice(client_ids) if random.random() < 0.88 else ""
        org_id = random.choice(org_ids) if (not client_id or random.random() < 0.42) else ""
        assignee_id = random.choice(user_ids) if status_code not in {"NEW", "CLOSED"} and random.random() < 0.82 else ""
        assigned_at = fake.date_time_between(start_date=created_dt, end_date=updated_dt, tzinfo=timezone.utc).isoformat() if assignee_id else ""
        resolved_at = updated_dt.isoformat() if status_code in {"APPROVED", "DENIED"} else ""
        closed_at = updated_dt.isoformat() if status_code == "CLOSED" else ""
        due_date = (created_dt.date() + timedelta(days=random.randint(7, 90))).isoformat() if status_code not in {"APPROVED", "DENIED", "CLOSED"} else ""
        reopened_count = random.choices([0, 1, 2, 3], weights=[94, 4, 1.5, 0.5], k=1)[0]
        rows.append((
            case_id, f"{DEMO_CASE_PREFIX}{i + 1:07d}", f"{title_base} - {fake.borough() if hasattr(fake, 'borough') else 'NYC'} #{i + 1}",
            description, statuses[status_code], priorities[priority_code], types[type_code], org_id, client_id,
            assignee_id, assigned_at, due_date, resolved_at, closed_at, reopened_count, False,
            created_dt.isoformat(), updated_dt.isoformat(), admin_id, admin_id,
        ))
    return ids, rows


def note_rows(fake: Faker, scale: Scale, admin_id: str, case_ids: list[str]) -> list[tuple]:
    rows = []
    for _ in range(scale.notes):
        created_at = fake.date_time_between(start_date="-30M", end_date="now", tzinfo=timezone.utc).isoformat()
        rows.append((
            str(uuid.uuid4()), random.choice(case_ids), random.choice(NOTE_BODIES),
            random.random() < 0.38, False, created_at, admin_id,
        ))
    return rows


def task_rows(fake: Faker, scale: Scale, refs: dict, case_ids: list[str]) -> list[tuple]:
    rows = []
    admin_id = refs["admin_id"]
    user_ids = refs["user_ids"] or [admin_id]
    statuses = refs["task_statuses"]
    for _ in range(scale.tasks):
        status = weighted_choice(TASK_STATUS_WEIGHTS)
        created_dt = fake.date_time_between(start_date="-24M", end_date="-1d", tzinfo=timezone.utc)
        completed_at = fake.date_time_between(start_date=created_dt, end_date="now", tzinfo=timezone.utc).isoformat() if status == "COMPLETED" else ""
        assigned_to = random.choice(user_ids) if random.random() < 0.72 else ""
        rows.append((
            str(uuid.uuid4()), random.choice(case_ids), random.choice(TASK_TITLES),
            "Synthetic BIR operational task", statuses[status], assigned_to,
            (date.today() + timedelta(days=random.randint(-30, 45))).isoformat() if status in {"PENDING", "IN_PROGRESS"} else "",
            completed_at, admin_id if completed_at else "", False, created_dt.isoformat(),
            created_dt.isoformat(), admin_id, admin_id,
        ))
    return rows


def attachment_rows(fake: Faker, scale: Scale, admin_id: str, case_ids: list[str]) -> list[tuple]:
    rows = []
    for i in range(scale.attachments):
        stem, mime, description = random.choice(ATTACHMENT_TYPES)
        case_id = random.choice(case_ids)
        ext = {"application/pdf": "pdf", "image/jpeg": "jpg", "text/csv": "csv", "message/rfc822": "eml"}[mime]
        created_at = fake.date_time_between(start_date="-24M", end_date="now", tzinfo=timezone.utc).isoformat()
        rows.append((
            str(uuid.uuid4()), case_id, f"{stem}-{i + 1:08d}.{ext}",
            f"/demo/bir/{case_id}/{stem}-{i + 1:08d}.{ext}", random.randint(12_000, 8_000_000),
            mime, description, False, created_at, admin_id,
        ))
    return rows


def validate(conn: psycopg.Connection) -> list[tuple[str, int]]:
    queries = [
        ("organizations", "SELECT count(*) FROM organizations WHERE external_id LIKE 'BIR-DEMO-ORG-%'"),
        ("clients", "SELECT count(*) FROM clients WHERE external_id LIKE 'BIR-DEMO-CLIENT-%'"),
        ("cases", "SELECT count(*) FROM cases WHERE case_number LIKE 'BIR-%'"),
        ("notes", "SELECT count(*) FROM case_notes WHERE case_id IN (SELECT id FROM cases WHERE case_number LIKE 'BIR-%')"),
        ("tasks", "SELECT count(*) FROM case_tasks WHERE case_id IN (SELECT id FROM cases WHERE case_number LIKE 'BIR-%')"),
        ("attachments", "SELECT count(*) FROM case_attachments WHERE case_id IN (SELECT id FROM cases WHERE case_number LIKE 'BIR-%')"),
    ]
    results = []
    with conn.cursor() as cur:
        for label, sql in queries:
            cur.execute(sql)
            results.append((label, int(cur.fetchone()[0])))
    return results


def main() -> int:
    args = parse_args()
    logging.basicConfig(level=args.log_level, format="%(asctime)s %(levelname)s %(message)s")
    random.seed(args.seed)
    Faker.seed(args.seed)
    fake = Faker("en_US")
    scale = Scale(args.organizations, args.clients, args.cases, args.notes, args.tasks, args.attachments)
    started = time.perf_counter()

    LOGGER.info("BIR demo scale: %s", scale)
    if args.dry_run:
        LOGGER.info("dry run enabled; no database writes will be performed")

    with psycopg.connect(args.db_url) as conn:
        refs = load_reference_data(conn)
        if args.reset_demo or args.reset_only:
            if args.dry_run:
                LOGGER.info("dry run: reset skipped")
            else:
                deleted = reset_demo(conn)
                conn.commit()
                LOGGER.info("reset complete: %s", deleted)
        if args.reset_only:
            for label, count in validate(conn):
                LOGGER.info("validation %s=%s", label, f"{count:,}")
            return 0

        org_ids, orgs = organization_rows(fake, scale, refs["admin_id"])
        client_ids, clients = client_rows(fake, scale, refs["admin_id"], org_ids)
        case_ids, cases = case_rows(fake, scale, refs, org_ids, client_ids)
        notes = note_rows(fake, scale, refs["admin_id"], case_ids)
        tasks = task_rows(fake, scale, refs, case_ids)
        attachments = attachment_rows(fake, scale, refs["admin_id"], case_ids)

        LOGGER.info(
            "generated rows organizations=%s clients=%s cases=%s notes=%s tasks=%s attachments=%s",
            len(orgs), len(clients), len(cases), len(notes), len(tasks), len(attachments),
        )
        if args.dry_run:
            LOGGER.info("dry run complete in %.2fs", time.perf_counter() - started)
            return 0

        copy_rows(conn, "organizations", [
            "id", "name", "external_id", "address_line1", "city", "state_province", "postal_code",
            "country", "phone", "email", "notes", "is_active", "is_deleted", "created_at", "updated_at", "created_by",
        ], orgs, "organizations", args.batch_size)
        copy_rows(conn, "clients", [
            "id", "organization_id", "first_name", "last_name", "middle_name", "date_of_birth", "email", "phone",
            "address_line1", "city", "state_province", "postal_code", "country", "external_id", "notes",
            "is_active", "is_deleted", "created_at", "updated_at", "created_by",
        ], clients, "clients", args.batch_size)
        copy_rows(conn, "cases", [
            "id", "case_number", "title", "description", "status_id", "priority_id", "type_id", "organization_id",
            "client_id", "assigned_to_id", "assigned_at", "due_date", "resolved_at", "closed_at", "reopened_count",
            "is_deleted", "created_at", "updated_at", "created_by", "updated_by",
        ], cases, "cases", args.batch_size)
        copy_rows(conn, "case_notes", [
            "id", "case_id", "body", "is_internal", "is_deleted", "created_at", "created_by",
        ], notes, "case_notes", args.batch_size)
        copy_rows(conn, "case_tasks", [
            "id", "case_id", "title", "description", "status_id", "assigned_to_id", "due_date",
            "completed_at", "completed_by", "is_deleted", "created_at", "updated_at", "created_by", "updated_by",
        ], tasks, "case_tasks", args.batch_size)
        copy_rows(conn, "case_attachments", [
            "id", "case_id", "original_filename", "storage_path", "file_size_bytes", "mime_type",
            "description", "is_deleted", "created_at", "created_by",
        ], attachments, "case_attachments", args.batch_size)
        conn.commit()

        LOGGER.info("validation results")
        for label, count in validate(conn):
            LOGGER.info("validation %s=%s", label, f"{count:,}")

    LOGGER.info("completed in %.2fs", time.perf_counter() - started)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
