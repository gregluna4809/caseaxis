#!/usr/bin/env python3
"""
Generate human-services synthetic demo data for CaseAxis.

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
import re
import sys
import time
import uuid
from dataclasses import dataclass
from datetime import date, datetime, timedelta, timezone
from typing import Sequence
from urllib.parse import quote, urlparse

try:
    import psycopg
    from faker import Faker
except ImportError as exc:
    print(
        "Missing dependency. Install with: python -m pip install Faker psycopg[binary]",
        file=sys.stderr,
    )
    raise SystemExit(2) from exc


LOGGER = logging.getLogger("seed_human_services_demo")
DEMO_CASE_PREFIX = "BIR-"
DEMO_EXTERNAL_PREFIX = "BIR-DEMO"
DEMO_EMAIL_DOMAIN = "mbra-demo.example"
DEMO_STAFF_PREFIX = "mbra-reviewer-"
DEFAULT_DB_URL = "postgresql://greg:mypass123@localhost:5434/caseaxis_local"
LOCK_TIMEOUT = "5s"
STATEMENT_TIMEOUT = "5min"

SCALE_PRESETS = {
    "small": {
        "organizations": 40,
        "clients": 1_000,
        "cases": 2_200,
        "notes": 4_400,
        "tasks": 3_300,
        "attachments": 1_650,
    },
    "full": {
        "organizations": 250,
        "clients": 25_000,
        "cases": 75_000,
        "notes": 150_000,
        "tasks": 100_000,
        "attachments": 50_000,
    },
}

AGENCY_NAME_PATTERNS = [
    "{area} Community Benefits Office",
    "{area} Family Assistance Center",
    "{area} Employment Support Center",
    "{area} Senior Services Bureau",
    "{area} Housing Stability Unit",
    "{area} Nutrition Access Program",
    "{area} Medical Assistance Desk",
    "{area} Appeals Resolution Unit",
    "{area} Documentation Help Center",
    "{area} Recipient Support Center",
    "{area} Public Benefits Intake Office",
    "{area} Determinations Review Unit",
]

AGENCY_AREAS = [
    "Brooklyn North",
    "Brooklyn South",
    "Central Brooklyn",
    "Coney Island",
    "Flatbush",
    "Williamsburg",
    "Queens East",
    "Queens West",
    "Jamaica",
    "Flushing",
    "Bronx North",
    "Bronx South",
    "South Bronx",
    "Upper Manhattan",
    "Lower Manhattan",
    "Harlem",
    "Staten Island",
    "Harbor",
    "Metro",
    "Kings County",
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
    ("COMPLAINT", "Benefit appeal", "Recipient appealed a benefit decision and requested supervisory review."),
    ("COMPLAINT", "Service access complaint", "Recipient reported difficulty accessing assigned benefits support."),
    ("APPLICATION", "Program eligibility review", "Review household information, income records, and program eligibility factors."),
    ("APPLICATION", "Benefit determination", "Complete benefit determination after documentation and verification review."),
    ("APPLICATION", "Renewal review", "Evaluate renewal materials for continued program participation."),
    ("INQUIRY", "Recipient inquiry", "Recipient requested guidance about benefit deadlines and required forms."),
    ("INQUIRY", "Document status inquiry", "Recipient asked for an update on submitted verification documents."),
    ("INVESTIGATION", "Verification review", "Verify submitted records against agency and recipient information."),
    ("INVESTIGATION", "Household composition review", "Confirm household membership and supporting records."),
    ("GENERAL", "Documentation request", "Collect missing supporting documentation for benefit review completion."),
    ("GENERAL", "Case reassessment", "Reassess benefit review due to changed household or service circumstances."),
    ("GENERAL", "Determination follow-up", "Follow up on completed determination and next-step notices."),
]

NOTE_BODIES = [
    "Initial intake completed. Required household and identity documents reviewed for completeness.",
    "Recipient contacted by phone; voicemail left with callback instructions and document deadline.",
    "Income verification reviewed against submitted pay records and agency guidance.",
    "Additional residency documentation requested from recipient.",
    "Supervisor review requested because the appeal includes conflicting eligibility information.",
    "Agency referral received and linked to the review record.",
    "Follow-up scheduled after receipt of missing documentation.",
    "Potential duplicate benefit review checked against prior case activity.",
    "Determination summary updated based on available evidence.",
    "Recipient notice prepared with deadline and next-step instructions.",
]

TASK_TITLES = [
    "Request supporting documentation",
    "Review eligibility factors",
    "Verify household information",
    "Prepare benefit determination",
    "Contact recipient",
    "Review appeal materials",
    "Confirm agency referral",
    "Schedule follow-up call",
    "Document verification outcome",
    "Prepare supervisory summary",
    "Review renewal packet",
    "Send determination notice",
]

ATTACHMENT_TYPES = [
    ("identity-verification", "application/pdf", "Identity verification document"),
    ("residency-document", "application/pdf", "Residency documentation"),
    ("income-records", "application/pdf", "Income verification records"),
    ("appeal-form", "application/pdf", "Signed benefit appeal form"),
    ("agency-referral", "application/pdf", "Agency referral packet"),
    ("recipient-email", "message/rfc822", "Recipient communication export"),
]

STAFF_ROSTER = [
    ("Alicia", "Bennett"),
    ("Marcus", "Reed"),
    ("Priya", "Shah"),
    ("Daniel", "Ortiz"),
    ("Jasmine", "Coleman"),
    ("Nora", "Kaplan"),
    ("Evan", "Brooks"),
    ("Camila", "Ramos"),
    ("Samuel", "Price"),
    ("Leah", "Foster"),
    ("Andre", "Williams"),
    ("Mina", "Park"),
    ("Grace", "Okafor"),
    ("Henry", "Lopez"),
    ("Tanya", "Fields"),
    ("Owen", "Gallagher"),
    ("Renee", "Washington"),
    ("Felix", "Morales"),
    ("Imani", "Johnson"),
    ("Victor", "Chen"),
    ("Elena", "Torres"),
    ("Malik", "Henderson"),
    ("Sofia", "Patel"),
    ("Caleb", "Nguyen"),
]

STATUS_WEIGHTS = {
    "NEW": 6,
    "ASSIGNED": 13,
    "IN_REVIEW": 22,
    "PENDING_INFO": 15,
    "ESCALATED": 7,
    "APPROVED": 17,
    "DENIED": 12,
    "CLOSED": 6,
    "REOPENED": 2,
}

PRIORITY_WEIGHTS = {"LOW": 18, "MEDIUM": 52, "HIGH": 24, "CRITICAL": 6}
TASK_STATUS_WEIGHTS = {"PENDING": 35, "IN_PROGRESS": 30, "COMPLETED": 28, "CANCELLED": 7}
AREA_CODES_BY_STATE = {
    "NY": ["212", "315", "347", "516", "518", "585", "607", "631", "646", "718", "845", "914", "917", "929"],
    "NJ": ["201", "551", "609", "732", "848", "856", "862", "908", "973"],
}

RESIDUAL_TERMS = ["robotics", "underwriting", "liability", "claims", "insurance"]


@dataclass(frozen=True)
class Scale:
    organizations: int
    clients: int
    cases: int
    notes: int
    tasks: int
    attachments: int


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Seed CaseAxis with human-services demo data.")
    parser.add_argument("--db-url", default=os.getenv("DB_URL"))
    parser.add_argument("--db-host", default=os.getenv("POSTGRES_HOST") or os.getenv("DB_HOST"))
    parser.add_argument("--db-port", type=int, default=int(os.getenv("POSTGRES_PORT") or os.getenv("DB_PORT") or "0") or None)
    parser.add_argument("--db-name", default=os.getenv("POSTGRES_DB") or os.getenv("DB_NAME"))
    parser.add_argument("--db-user", default=os.getenv("POSTGRES_USER") or os.getenv("DB_USER"))
    parser.add_argument("--db-password", default=os.getenv("POSTGRES_PASSWORD") or os.getenv("DB_PASSWORD"))
    parser.add_argument("--scale", choices=sorted(SCALE_PRESETS), default=os.getenv("SEED_SCALE", "small"))
    parser.add_argument("--organizations", type=int)
    parser.add_argument("--clients", type=int)
    parser.add_argument("--cases", type=int)
    parser.add_argument("--notes", type=int)
    parser.add_argument("--tasks", type=int)
    parser.add_argument("--attachments", type=int)
    parser.add_argument("--staff", type=int, default=24)
    parser.add_argument("--seed", type=int, default=4809)
    parser.add_argument("--dry-run", action="store_true", help="Generate and validate in memory without writing.")
    parser.add_argument("--reset-demo", action="store_true", help="Delete previously seeded demo rows before inserting.")
    parser.add_argument("--reset-only", action="store_true", help="Delete demo rows and exit.")
    parser.add_argument("--terminate-sessions", action="store_true", help="Terminate other sessions connected to the target database before reset/reload.")
    parser.add_argument("--batch-size", type=int, default=10_000)
    parser.add_argument("--log-level", default="INFO", choices=["DEBUG", "INFO", "WARNING", "ERROR"])
    return parser.parse_args()


def resolve_scale(args: argparse.Namespace) -> Scale:
    preset = SCALE_PRESETS[args.scale]
    return Scale(
        args.organizations if args.organizations is not None else preset["organizations"],
        args.clients if args.clients is not None else preset["clients"],
        args.cases if args.cases is not None else preset["cases"],
        args.notes if args.notes is not None else preset["notes"],
        args.tasks if args.tasks is not None else preset["tasks"],
        args.attachments if args.attachments is not None else preset["attachments"],
    )


def resolve_db_url(args: argparse.Namespace) -> str:
    if args.db_url:
        return args.db_url

    if not any([args.db_host, args.db_port, args.db_name, args.db_user, args.db_password]):
        return DEFAULT_DB_URL

    host = args.db_host or "localhost"
    port = args.db_port or 5434
    name = args.db_name or "caseaxis_local"
    user = args.db_user or "greg"
    password = args.db_password or "mypass123"
    return f"postgresql://{quote(user)}:{quote(password)}@{host}:{port}/{name}"


def describe_db_target(db_url: str) -> str:
    parsed = urlparse(db_url)
    host = parsed.hostname or "localhost"
    port = parsed.port or 5432
    database = parsed.path.lstrip("/") or "-"
    user = parsed.username or "-"
    return f"{user}@{host}:{port}/{database}"


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


def progress_interval(total: int) -> int:
    return max(1, min(10_000, total // 10 or 1))


def copy_rows(
    conn: psycopg.Connection,
    table: str,
    columns: list[str],
    rows: Sequence[tuple],
    label: str,
    batch_size: int,
) -> int:
    total = len(rows)
    copied = 0
    sql = f"COPY {table} ({', '.join(columns)}) FROM STDIN WITH (FORMAT CSV, NULL '')"
    LOGGER.info("loading %s: %s rows", label, f"{total:,}")
    while copied < total:
        batch = rows[copied:copied + batch_size]
        with conn.cursor() as cur:
            with cur.copy(sql) as copy:
                buffer = io.StringIO()
                writer = csv.writer(buffer, lineterminator="\n")
                writer.writerows(batch)
                copy.write(buffer.getvalue())
        conn.commit()
        copied += len(batch)
        LOGGER.info("loaded %s rows into %s (%s/%s)", f"{len(batch):,}", label, f"{copied:,}", f"{total:,}")
    if total == 0:
        conn.commit()
    LOGGER.info("finished %s: %s rows", label, f"{copied:,}")
    return copied


def configure_seed_session(conn: psycopg.Connection) -> None:
    with conn.cursor() as cur:
        cur.execute("SELECT set_config('lock_timeout', %s, false)", (LOCK_TIMEOUT,))
        cur.execute("SELECT set_config('statement_timeout', %s, false)", (STATEMENT_TIMEOUT,))
        cur.execute("SELECT set_config('application_name', 'caseaxis_demo_seeder', false)")
    conn.commit()
    LOGGER.info("database timeouts: lock_timeout=%s statement_timeout=%s", LOCK_TIMEOUT, STATEMENT_TIMEOUT)


def terminate_conflicting_sessions(conn: psycopg.Connection) -> int:
    with conn.cursor() as cur:
        cur.execute("""
            SELECT pg_terminate_backend(pid)
            FROM pg_stat_activity
            WHERE datname = current_database()
              AND pid <> pg_backend_pid()
              AND backend_type = 'client backend'
            """)
        terminated = sum(1 for (ok,) in cur.fetchall() if ok)
    conn.commit()
    LOGGER.warning("terminated %s conflicting database session(s)", terminated)
    return terminated


def load_reference_data(conn: psycopg.Connection) -> dict[str, dict[str, str] | list[str] | str]:
    with conn.cursor() as cur:
        cur.execute("SELECT id FROM users WHERE username = 'demo' AND is_deleted = FALSE")
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

        cur.execute("""
            SELECT id
            FROM users
            WHERE username LIKE %s
              AND is_active = TRUE
              AND is_deleted = FALSE
            ORDER BY username
            """, (f"{DEMO_STAFF_PREFIX}%",))
        refs["staff_user_ids"] = [str(row[0]) for row in cur.fetchall()]
    return refs


def reset_demo(conn: psycopg.Connection) -> dict[str, int]:
    LOGGER.info("resetting existing human-services demo rows")
    statements = [
        ("attachments", "DELETE FROM case_attachments WHERE case_id IN (SELECT id FROM cases WHERE case_number LIKE %s)", (f"{DEMO_CASE_PREFIX}%",)),
        ("notes", "DELETE FROM case_notes WHERE case_id IN (SELECT id FROM cases WHERE case_number LIKE %s)", (f"{DEMO_CASE_PREFIX}%",)),
        ("tasks", "DELETE FROM case_tasks WHERE case_id IN (SELECT id FROM cases WHERE case_number LIKE %s)", (f"{DEMO_CASE_PREFIX}%",)),
        ("assignments", "DELETE FROM case_assignments WHERE case_id IN (SELECT id FROM cases WHERE case_number LIKE %s)", (f"{DEMO_CASE_PREFIX}%",)),
        ("status_history", "DELETE FROM case_status_history WHERE case_id IN (SELECT id FROM cases WHERE case_number LIKE %s)", (f"{DEMO_CASE_PREFIX}%",)),
        ("cases", "DELETE FROM cases WHERE case_number LIKE %s", (f"{DEMO_CASE_PREFIX}%",)),
        ("clients", "DELETE FROM clients WHERE external_id LIKE %s", (f"{DEMO_EXTERNAL_PREFIX}-CLIENT-%",)),
        ("organizations", "DELETE FROM organizations WHERE external_id LIKE %s", (f"{DEMO_EXTERNAL_PREFIX}-ORG-%",)),
        ("staff_roles", "DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE %s)", (f"{DEMO_STAFF_PREFIX}%",)),
        ("staff", "DELETE FROM users WHERE username LIKE %s", (f"{DEMO_STAFF_PREFIX}%",)),
    ]
    deleted: dict[str, int] = {}
    for label, sql, params in statements:
        LOGGER.info("reset phase %s: deleting prior demo rows", label)
        with conn.cursor() as cur:
            cur.execute(sql, params)
            deleted[label] = cur.rowcount
            LOGGER.info("deleted %s %s rows", f"{cur.rowcount:,}", label)
        conn.commit()
    return deleted


def seed_staff(conn: psycopg.Connection, admin_id: str, staff_count: int) -> list[str]:
    count = max(15, min(staff_count, len(STAFF_ROSTER)))
    selected = STAFF_ROSTER[:count]
    with conn.cursor() as cur:
        cur.execute("SELECT password_hash FROM users WHERE id = %s", (admin_id,))
        password_hash = cur.fetchone()[0]
        cur.execute("SELECT id FROM roles WHERE code = 'CASE_WORKER' AND is_active = TRUE")
        role_row = cur.fetchone()
        role_id = role_row[0] if role_row else None

        staff_ids: list[str] = []
        for idx, (first, last) in enumerate(selected, start=1):
            user_id = str(uuid.uuid5(uuid.NAMESPACE_DNS, f"caseaxis:{DEMO_STAFF_PREFIX}{idx}"))
            username = f"{DEMO_STAFF_PREFIX}{idx:02d}"
            email = f"{username}@{DEMO_EMAIL_DOMAIN}"
            cur.execute("""
                INSERT INTO users (
                    id, username, email, password_hash, first_name, last_name,
                    is_active, is_deleted, deleted_at, deleted_by, created_by, updated_by
                )
                VALUES (%s, %s, %s, %s, %s, %s, TRUE, FALSE, NULL, NULL, %s, %s)
                ON CONFLICT (username) DO UPDATE SET
                    email = EXCLUDED.email,
                    password_hash = EXCLUDED.password_hash,
                    first_name = EXCLUDED.first_name,
                    last_name = EXCLUDED.last_name,
                    is_active = TRUE,
                    is_deleted = FALSE,
                    deleted_at = NULL,
                    deleted_by = NULL,
                    updated_by = EXCLUDED.updated_by
                RETURNING id
                """, (user_id, username, email, password_hash, first, last, admin_id, admin_id))
            staff_id = str(cur.fetchone()[0])
            staff_ids.append(staff_id)

            if role_id is not None:
                cur.execute("""
                    INSERT INTO user_roles (id, user_id, role_id, assigned_by)
                    VALUES (%s, %s, %s, %s)
                    ON CONFLICT DO NOTHING
                    """, (str(uuid.uuid4()), staff_id, role_id, admin_id))
    LOGGER.info("seeded %s reviewer/staff users", len(staff_ids))
    return staff_ids


def organization_rows(fake: Faker, scale: Scale, admin_id: str) -> tuple[list[str], list[tuple]]:
    ids: list[str] = []
    rows: list[tuple] = []
    names: list[str] = []
    for area in AGENCY_AREAS:
        for pattern in AGENCY_NAME_PATTERNS:
            names.append(pattern.format(area=area))
    random.shuffle(names)

    for i in range(scale.organizations):
        org_id = str(uuid.uuid4())
        ids.append(org_id)
        city, state, postal = weighted_area()
        if i < len(names):
            name = names[i]
        else:
            name = f"{random.choice(AGENCY_AREAS)} {random.choice(['Benefits Access Office', 'Appeals Review Center', 'Family Support Desk'])} {i + 1}"
        created_at = fake.date_time_between(start_date="-4y", end_date="-30d", tzinfo=timezone.utc).isoformat()
        rows.append((
            org_id, name[:255], f"{DEMO_EXTERNAL_PREFIX}-ORG-{i + 1:06d}", fake.street_address(), city, state,
            postal, "USA", realistic_us_phone(state, include_extension=True), f"agency-{i + 1}@{DEMO_EMAIL_DOMAIN}",
            "Synthetic human-services partner agency", True, False, created_at, created_at, admin_id,
        ))
        if (i + 1) % progress_interval(scale.organizations) == 0 or i + 1 == scale.organizations:
            LOGGER.info("generated organizations %s/%s", f"{i + 1:,}", f"{scale.organizations:,}")
    return ids, rows


def client_rows(fake: Faker, scale: Scale, admin_id: str, org_ids: list[str]) -> tuple[list[str], list[tuple]]:
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
            f"{first}.{last}.{i + 1}@{DEMO_EMAIL_DOMAIN}".lower().replace("'", ""),
            realistic_us_phone(state), fake.street_address(), city, state, postal, "USA",
            f"{DEMO_EXTERNAL_PREFIX}-CLIENT-{i + 1:08d}", "Synthetic human-services recipient",
            True, False, created_at, created_at, admin_id,
        ))
        if (i + 1) % progress_interval(scale.clients) == 0 or i + 1 == scale.clients:
            LOGGER.info("generated clients %s/%s", f"{i + 1:,}", f"{scale.clients:,}")
    return ids, rows


def case_rows(fake: Faker, scale: Scale, refs: dict, org_ids: list[str], client_ids: list[str]) -> tuple[list[str], list[tuple]]:
    ids: list[str] = []
    rows: list[tuple] = []
    admin_id = refs["admin_id"]
    staff_ids = refs["staff_user_ids"] or [admin_id]
    statuses = refs["statuses"]
    priorities = refs["priorities"]
    types = refs["types"]
    assignee_weights = [1.0 + ((idx % 5) * 0.18) for idx, _ in enumerate(staff_ids)]

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
        assignment_probability = 0.62 if status_code == "NEW" else 0.82
        assignee_id = random.choices(staff_ids, weights=assignee_weights, k=1)[0] if random.random() < assignment_probability else ""
        assigned_at = fake.date_time_between(start_date=created_dt, end_date=updated_dt, tzinfo=timezone.utc).isoformat() if assignee_id else ""
        resolved_at = updated_dt.isoformat() if status_code in {"APPROVED", "DENIED"} else ""
        closed_at = updated_dt.isoformat() if status_code == "CLOSED" else ""
        due_date = (created_dt.date() + timedelta(days=random.randint(7, 90))).isoformat() if status_code not in {"APPROVED", "DENIED", "CLOSED"} else ""
        reopened_count = random.choices([0, 1, 2, 3], weights=[94, 4, 1.5, 0.5], k=1)[0]
        household = random.choice(["household", "recipient", "family", "senior", "applicant"])
        rows.append((
            case_id, f"{DEMO_CASE_PREFIX}{i + 1:07d}", f"{title_base} for {fake.last_name()} {household} #{i + 1}",
            description, statuses[status_code], priorities[priority_code], types[type_code], org_id, client_id,
            assignee_id, assigned_at, due_date, resolved_at, closed_at, reopened_count, False,
            created_dt.isoformat(), updated_dt.isoformat(), admin_id, admin_id,
        ))
        if (i + 1) % progress_interval(scale.cases) == 0 or i + 1 == scale.cases:
            LOGGER.info("generated cases %s/%s", f"{i + 1:,}", f"{scale.cases:,}")
    return ids, rows


def note_rows(fake: Faker, scale: Scale, refs: dict, case_ids: list[str]) -> list[tuple]:
    rows = []
    admin_id = refs["admin_id"]
    staff_ids = refs["staff_user_ids"] or [admin_id]
    for i in range(scale.notes):
        created_at = fake.date_time_between(start_date="-30M", end_date="now", tzinfo=timezone.utc).isoformat()
        rows.append((
            str(uuid.uuid4()), random.choice(case_ids), random.choice(NOTE_BODIES),
            random.random() < 0.38, False, created_at, random.choice(staff_ids),
        ))
        if (i + 1) % progress_interval(scale.notes) == 0 or i + 1 == scale.notes:
            LOGGER.info("generated notes %s/%s", f"{i + 1:,}", f"{scale.notes:,}")
    return rows


def task_rows(fake: Faker, scale: Scale, refs: dict, case_ids: list[str]) -> list[tuple]:
    rows = []
    admin_id = refs["admin_id"]
    staff_ids = refs["staff_user_ids"] or [admin_id]
    statuses = refs["task_statuses"]
    for i in range(scale.tasks):
        status = weighted_choice(TASK_STATUS_WEIGHTS)
        created_dt = fake.date_time_between(start_date="-24M", end_date="-1d", tzinfo=timezone.utc)
        completed_at = fake.date_time_between(start_date=created_dt, end_date="now", tzinfo=timezone.utc).isoformat() if status == "COMPLETED" else ""
        assigned_to = random.choice(staff_ids) if random.random() < 0.72 else ""
        rows.append((
            str(uuid.uuid4()), random.choice(case_ids), random.choice(TASK_TITLES),
            "Human-services review action for benefit follow-up.", statuses[status], assigned_to,
            (date.today() + timedelta(days=random.randint(-30, 45))).isoformat() if status in {"PENDING", "IN_PROGRESS"} else "",
            completed_at, random.choice(staff_ids) if completed_at else "", False, created_dt.isoformat(),
            created_dt.isoformat(), admin_id, admin_id,
        ))
        if (i + 1) % progress_interval(scale.tasks) == 0 or i + 1 == scale.tasks:
            LOGGER.info("generated tasks %s/%s", f"{i + 1:,}", f"{scale.tasks:,}")
    return rows


def attachment_rows(fake: Faker, scale: Scale, admin_id: str, case_ids: list[str]) -> list[tuple]:
    rows = []
    for i in range(scale.attachments):
        stem, mime, description = random.choice(ATTACHMENT_TYPES)
        case_id = random.choice(case_ids)
        ext = {"application/pdf": "pdf", "message/rfc822": "eml"}[mime]
        created_at = fake.date_time_between(start_date="-24M", end_date="now", tzinfo=timezone.utc).isoformat()
        rows.append((
            str(uuid.uuid4()), case_id, f"{stem}-{i + 1:08d}.{ext}",
            f"/demo/human-services/{case_id}/{stem}-{i + 1:08d}.{ext}", random.randint(12_000, 8_000_000),
            mime, description, False, created_at, admin_id,
        ))
        if (i + 1) % progress_interval(scale.attachments) == 0 or i + 1 == scale.attachments:
            LOGGER.info("generated attachments %s/%s", f"{i + 1:,}", f"{scale.attachments:,}")
    return rows


def validate(conn: psycopg.Connection) -> list[tuple[str, int]]:
    queries = [
        ("organizations", "SELECT count(*) FROM organizations WHERE external_id LIKE 'BIR-DEMO-ORG-%'"),
        ("clients", "SELECT count(*) FROM clients WHERE external_id LIKE 'BIR-DEMO-CLIENT-%'"),
        ("cases", "SELECT count(*) FROM cases WHERE case_number LIKE 'BIR-%'"),
        ("notes", "SELECT count(*) FROM case_notes WHERE case_id IN (SELECT id FROM cases WHERE case_number LIKE 'BIR-%')"),
        ("tasks", "SELECT count(*) FROM case_tasks WHERE case_id IN (SELECT id FROM cases WHERE case_number LIKE 'BIR-%')"),
        ("attachments", "SELECT count(*) FROM case_attachments WHERE case_id IN (SELECT id FROM cases WHERE case_number LIKE 'BIR-%')"),
        ("staff", "SELECT count(*) FROM users WHERE username LIKE 'mbra-reviewer-%' AND is_deleted = FALSE"),
    ]
    results = []
    with conn.cursor() as cur:
        for label, sql in queries:
            cur.execute(sql)
            results.append((label, int(cur.fetchone()[0])))
    return results


def print_data_check(conn: psycopg.Connection) -> None:
    with conn.cursor() as cur:
        cur.execute("SELECT count(DISTINCT name) FROM organizations WHERE external_id LIKE 'BIR-DEMO-ORG-%'")
        print(f"distinct_org_name_count: {cur.fetchone()[0]}")

        cur.execute("""
            SELECT count(DISTINCT concat_ws(' ', u.first_name, u.last_name))
            FROM cases c
            JOIN users u ON u.id = c.assigned_to_id
            WHERE c.case_number LIKE 'BIR-%'
            """)
        print(f"distinct_assignee_name_count: {cur.fetchone()[0]}")

        print("assignee_distribution_top:")
        cur.execute("""
            SELECT coalesce(nullif(concat_ws(' ', u.first_name, u.last_name), ''), 'Unassigned') AS assignee, count(*) AS cases
            FROM cases c
            LEFT JOIN users u ON u.id = c.assigned_to_id
            WHERE c.case_number LIKE 'BIR-%'
            GROUP BY assignee
            ORDER BY cases DESC
            LIMIT 12
            """)
        for assignee, count in cur.fetchall():
            print(f"  {assignee}: {count}")

        print("status_distribution:")
        cur.execute("""
            SELECT s.code, count(*) AS cases
            FROM cases c
            JOIN case_statuses s ON s.id = c.status_id
            WHERE c.case_number LIKE 'BIR-%'
            GROUP BY s.code
            ORDER BY s.code
            """)
        for status, count in cur.fetchall():
            print(f"  {status}: {count}")

        cur.execute("""
            SELECT
              count(*) FILTER (WHERE assigned_to_id IS NULL) AS unassigned,
              count(*) AS total
            FROM cases
            WHERE case_number LIKE 'BIR-%'
            """)
        unassigned, total = cur.fetchone()
        share = 0 if total == 0 else (unassigned / total) * 100
        print(f"unassigned_share: {unassigned}/{total} ({share:.1f}%)")


def generated_text(rows_by_name: dict[str, list[tuple]]) -> str:
    chunks: list[str] = []
    for rows in rows_by_name.values():
        for row in rows:
            chunks.extend(str(value) for value in row if isinstance(value, str))
    return "\n".join(chunks)


def residual_hits(text: str) -> dict[str, int]:
    return {term: len(re.findall(rf"\b{re.escape(term)}\b", text, flags=re.IGNORECASE)) for term in RESIDUAL_TERMS}


def is_lock_or_timeout_error(exc: BaseException) -> bool:
    sqlstate = getattr(exc, "sqlstate", None)
    return sqlstate in {"55P03", "57014"} or isinstance(
        exc,
        (
            psycopg.errors.LockNotAvailable,
            psycopg.errors.QueryCanceled,
        ),
    )


def log_lock_timeout_error(db_target: str, exc: BaseException) -> None:
    LOGGER.error(
        "couldn't acquire table lock while reloading demo data on %s. "
        "Stop the backend or rerun with --terminate-sessions. Database detail: %s",
        db_target,
        str(exc).strip(),
    )


def main() -> int:
    args = parse_args()
    logging.basicConfig(level=args.log_level, format="%(asctime)s %(levelname)s %(message)s", force=True)
    random.seed(args.seed)
    Faker.seed(args.seed)
    fake = Faker("en_US")
    scale = resolve_scale(args)
    db_url = resolve_db_url(args)
    db_target = describe_db_target(db_url)
    started = time.perf_counter()

    LOGGER.info("database target: %s", db_target)
    LOGGER.info("seed scale preset=%s resolved=%s", args.scale, scale)
    LOGGER.info("human-services demo scale: %s", scale)
    if args.dry_run:
        LOGGER.info("dry run enabled; no database writes will be performed")

    try:
        with psycopg.connect(db_url, connect_timeout=10) as conn:
            configure_seed_session(conn)
            if args.terminate_sessions and not args.dry_run:
                terminate_conflicting_sessions(conn)

            initial_refs = load_reference_data(conn)
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

            staff_ids = list(initial_refs.get("staff_user_ids", []))
            if not args.dry_run:
                staff_ids = seed_staff(conn, str(initial_refs["admin_id"]), args.staff)
                conn.commit()

            refs = load_reference_data(conn)
            if args.dry_run and not refs.get("staff_user_ids"):
                refs["staff_user_ids"] = staff_ids or [str(initial_refs["admin_id"])]

            org_ids, orgs = organization_rows(fake, scale, str(refs["admin_id"]))
            client_ids, clients = client_rows(fake, scale, str(refs["admin_id"]), org_ids)
            case_ids, cases = case_rows(fake, scale, refs, org_ids, client_ids)
            notes = note_rows(fake, scale, refs, case_ids)
            tasks = task_rows(fake, scale, refs, case_ids)
            attachments = attachment_rows(fake, scale, str(refs["admin_id"]), case_ids)

            rows_by_name = {
                "organizations": orgs,
                "clients": clients,
                "cases": cases,
                "notes": notes,
                "tasks": tasks,
                "attachments": attachments,
            }
            hits = residual_hits(generated_text(rows_by_name))
            LOGGER.info("residual source-domain term hits in generated rows: %s", hits)
            if any(hits.values()):
                raise RuntimeError(f"Generated rows contain residual source-domain terms: {hits}")

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
            ], attachments, "attachments", args.batch_size)
            conn.commit()

            LOGGER.info("validation results")
            for label, count in validate(conn):
                LOGGER.info("validation %s=%s", label, f"{count:,}")
            print_data_check(conn)
    except psycopg.Error as exc:
        if is_lock_or_timeout_error(exc):
            log_lock_timeout_error(db_target, exc)
            return 3
        raise

    LOGGER.info("completed in %.2fs", time.perf_counter() - started)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
