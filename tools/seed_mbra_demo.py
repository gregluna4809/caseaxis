#!/usr/bin/env python3
"""Seed local CaseAxis development data with an MBRA human-services demo set."""

from __future__ import annotations

import argparse
import csv
import io
import logging
import os
import random
import sys
import uuid
from datetime import date, datetime, timedelta, timezone
from typing import Iterable

try:
    import psycopg
    from faker import Faker
except ImportError as exc:
    print("Missing dependency. Install with: python -m pip install Faker psycopg[binary]", file=sys.stderr)
    raise SystemExit(2) from exc

LOGGER = logging.getLogger("seed_mbra_demo")
DEFAULT_DB_URL = "postgresql://greg:mypass123@localhost:5434/caseaxis_local"
CASE_PREFIX = "MBRA-"
EXTERNAL_PREFIX = "MBRA-DEMO"

AGENCY_SUFFIXES = [
    "Family Assistance Center", "Community Benefits Office", "Housing Support Unit",
    "Nutrition Access Program", "Medical Assistance Desk", "Senior Services Bureau",
    "Employment Support Center", "Appeals Resolution Office", "Documentation Help Center",
]

NYC_AREAS = [
    ("Brooklyn", "NY", ["11201", "11206", "11215", "11221", "11233"], 34),
    ("Queens", "NY", ["11354", "11368", "11372", "11432", "11691"], 24),
    ("Bronx", "NY", ["10451", "10453", "10457", "10461", "10467"], 18),
    ("New York", "NY", ["10002", "10011", "10027", "10029", "10035"], 16),
    ("Staten Island", "NY", ["10301", "10304", "10306", "10314"], 8),
]

REVIEW_SCENARIOS = [
    ("APPLICATION", "Program eligibility review", "Review household information, income documentation, and program eligibility factors."),
    ("APPLICATION", "Benefit determination", "Complete benefit determination after documentation and verification review."),
    ("COMPLAINT", "Benefit appeal", "Recipient appealed a prior benefit decision and requested supervisory review."),
    ("INQUIRY", "Recipient inquiry", "Recipient requested guidance about benefits, deadlines, and supporting documentation."),
    ("INVESTIGATION", "Verification review", "Verify submitted records against agency and recipient information."),
    ("GENERAL", "Case reassessment", "Reassess benefit review due to changed household or service circumstances."),
    ("GENERAL", "Documentation request", "Collect missing supporting documentation for benefit review completion."),
]

ACTION_TITLES = [
    "Request supporting documentation", "Review eligibility factors", "Verify household information",
    "Prepare benefit determination", "Contact recipient", "Review appeal materials",
    "Confirm agency referral", "Schedule follow-up call", "Document verification outcome",
    "Prepare supervisory summary",
]

STATUS_ACTIVE = ["NEW", "ASSIGNED", "IN_REVIEW", "PENDING_INFO", "ESCALATED", "REOPENED"]
STATUS_DETERMINED = ["APPROVED", "DENIED", "CLOSED"]
PRIORITIES = ["LOW", "MEDIUM", "MEDIUM", "MEDIUM", "HIGH", "HIGH", "CRITICAL"]
TASK_STATUSES = ["PENDING", "PENDING", "IN_PROGRESS", "IN_PROGRESS", "COMPLETED", "CANCELLED"]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Seed MBRA local demo data.")
    parser.add_argument("--db-url", default=os.getenv("DB_URL", DEFAULT_DB_URL))
    parser.add_argument("--recipients", type=int, default=750)
    parser.add_argument("--agencies", type=int, default=30)
    parser.add_argument("--reviews", type=int, default=2000)
    parser.add_argument("--actions", type=int, default=750)
    parser.add_argument("--determinations", type=int, default=500)
    parser.add_argument("--seed", type=int, default=20260620)
    parser.add_argument("--reset-demo", action="store_true")
    parser.add_argument("--reset-only", action="store_true")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--batch-size", type=int, default=5000)
    return parser.parse_args()


def weighted_area() -> tuple[str, str, str]:
    city, state, zips, _ = random.choices(NYC_AREAS, weights=[row[3] for row in NYC_AREAS], k=1)[0]
    return city, state, random.choice(zips)


def phone() -> str:
    return f"({random.choice(['212', '347', '646', '718', '917', '929'])}) {random.randint(200, 999):03d}-{random.randint(0, 9999):04d}"


def copy_rows(conn: psycopg.Connection, table: str, columns: list[str], rows: Iterable[tuple], batch_size: int) -> int:
    count = 0
    with conn.cursor() as cur:
        with cur.copy(f"COPY {table} ({', '.join(columns)}) FROM STDIN WITH (FORMAT CSV, NULL '')") as copy:
            buf = io.StringIO()
            writer = csv.writer(buf, lineterminator="\n")
            for row in rows:
                writer.writerow(row)
                count += 1
                if count % batch_size == 0:
                    copy.write(buf.getvalue())
                    buf.seek(0)
                    buf.truncate(0)
            if buf.tell():
                copy.write(buf.getvalue())
    LOGGER.info("inserted %s %s", f"{count:,}", table)
    return count


def refs(conn: psycopg.Connection) -> dict:
    out: dict = {}
    with conn.cursor() as cur:
        cur.execute("SELECT id FROM users WHERE username='admin' AND is_deleted=FALSE")
        out["admin_id"] = str(cur.fetchone()[0])
        cur.execute("SELECT id FROM users WHERE is_active=TRUE AND is_deleted=FALSE")
        out["user_ids"] = [str(row[0]) for row in cur.fetchall()]
        for table, key in [("case_statuses", "statuses"), ("case_priorities", "priorities"), ("case_types", "types"), ("task_statuses", "task_statuses")]:
            cur.execute(f"SELECT code, id FROM {table} WHERE is_active=TRUE")
            out[key] = {code: str(id_) for code, id_ in cur.fetchall()}
    return out


def reset(conn: psycopg.Connection) -> None:
    statements = [
        "DELETE FROM case_attachments WHERE case_id IN (SELECT id FROM cases WHERE case_number LIKE %s)",
        "DELETE FROM case_notes WHERE case_id IN (SELECT id FROM cases WHERE case_number LIKE %s)",
        "DELETE FROM case_tasks WHERE case_id IN (SELECT id FROM cases WHERE case_number LIKE %s)",
        "DELETE FROM case_assignments WHERE case_id IN (SELECT id FROM cases WHERE case_number LIKE %s)",
        "DELETE FROM case_status_history WHERE case_id IN (SELECT id FROM cases WHERE case_number LIKE %s)",
        "DELETE FROM cases WHERE case_number LIKE %s",
    ]
    with conn.cursor() as cur:
        for sql in statements:
            cur.execute(sql, (f"{CASE_PREFIX}%",))
        cur.execute("DELETE FROM clients WHERE external_id LIKE %s", (f"{EXTERNAL_PREFIX}-RECIPIENT-%",))
        cur.execute("DELETE FROM organizations WHERE external_id LIKE %s", (f"{EXTERNAL_PREFIX}-AGENCY-%",))


def build_rows(fake: Faker, cfg: argparse.Namespace, r: dict) -> dict[str, list[tuple]]:
    admin_id = r["admin_id"]
    user_ids = r["user_ids"] or [admin_id]
    now = datetime.now(timezone.utc)
    agencies, recipients, reviews, actions, notes = [], [], [], [], []
    agency_ids, recipient_ids, review_ids = [], [], []

    for i in range(cfg.agencies):
        city, state, postal = weighted_area()
        agency_id = str(uuid.uuid4())
        agency_ids.append(agency_id)
        created = fake.date_time_between(start_date="-4y", end_date="-60d", tzinfo=timezone.utc).isoformat()
        name = f"{random.choice(['Metropolitan', 'North Borough', 'Harbor', 'Eastside', 'Community'])} {random.choice(AGENCY_SUFFIXES)}"
        agencies.append((agency_id, f"{name} {i + 1}", f"{EXTERNAL_PREFIX}-AGENCY-{i + 1:05d}", fake.street_address(), city, state, postal, "USA", phone(), f"agency{i+1}@mbra-demo.example", "Synthetic MBRA partner agency", True, False, created, created, admin_id))

    for i in range(cfg.recipients):
        city, state, postal = weighted_area()
        recipient_id = str(uuid.uuid4())
        recipient_ids.append(recipient_id)
        first, last = fake.first_name(), fake.last_name()
        created = fake.date_time_between(start_date="-4y", end_date="-15d", tzinfo=timezone.utc).isoformat()
        recipients.append((recipient_id, random.choice(agency_ids) if random.random() < 0.28 else "", first, last, fake.first_name() if random.random() < 0.12 else "", fake.date_of_birth(minimum_age=18, maximum_age=89).isoformat(), f"{first}.{last}.{i+1}@mbra-demo.example".lower().replace("'", ""), phone(), fake.street_address(), city, state, postal, "USA", f"{EXTERNAL_PREFIX}-RECIPIENT-{i + 1:06d}", "Synthetic MBRA recipient", True, False, created, created, admin_id))

    determined_indexes = set(random.sample(range(cfg.reviews), cfg.determinations))
    for i in range(cfg.reviews):
        review_id = str(uuid.uuid4())
        review_ids.append(review_id)
        type_code, title, description = random.choice(REVIEW_SCENARIOS)
        status_code = random.choice(STATUS_DETERMINED if i in determined_indexes else STATUS_ACTIVE)
        created_dt = fake.date_time_between(start_date="-18M", end_date="-1d", tzinfo=timezone.utc)
        updated_dt = fake.date_time_between(start_date=created_dt, end_date=now, tzinfo=timezone.utc)
        assignee = random.choice(user_ids) if status_code != "NEW" and random.random() < 0.86 else ""
        determined_at = updated_dt.isoformat() if status_code in STATUS_DETERMINED else ""
        reviews.append((review_id, f"{CASE_PREFIX}{i + 1:07d}", f"{title} for {fake.last_name()} household", description, r["statuses"][status_code], r["priorities"][random.choice(PRIORITIES)], r["types"][type_code], random.choice(agency_ids) if random.random() < 0.35 else "", random.choice(recipient_ids), assignee, fake.date_time_between(start_date=created_dt, end_date=updated_dt, tzinfo=timezone.utc).isoformat() if assignee else "", "" if status_code in STATUS_DETERMINED else (created_dt.date() + timedelta(days=random.randint(5, 75))).isoformat(), determined_at if status_code in ["APPROVED", "DENIED"] else "", determined_at if status_code == "CLOSED" else "", random.choice([0, 0, 0, 0, 1]), False, created_dt.isoformat(), updated_dt.isoformat(), admin_id, admin_id))

    for i in range(cfg.actions):
        status = random.choice(TASK_STATUSES)
        created_dt = fake.date_time_between(start_date="-12M", end_date="-1d", tzinfo=timezone.utc)
        completed = fake.date_time_between(start_date=created_dt, end_date=now, tzinfo=timezone.utc).isoformat() if status == "COMPLETED" else ""
        actions.append((str(uuid.uuid4()), random.choice(review_ids), random.choice(ACTION_TITLES), "MBRA review action for benefit review follow-up.", r["task_statuses"][status], random.choice(user_ids) if random.random() < 0.78 else "", "" if completed else (date.today() + timedelta(days=random.randint(-21, 45))).isoformat(), completed, admin_id if completed else "", False, created_dt.isoformat(), created_dt.isoformat(), admin_id, admin_id))

    return {"agencies": agencies, "recipients": recipients, "reviews": reviews, "actions": actions}


def counts(conn: psycopg.Connection) -> dict[str, int]:
    queries = {
        "agencies": "SELECT count(*) FROM organizations WHERE external_id LIKE 'MBRA-DEMO-AGENCY-%'",
        "recipients": "SELECT count(*) FROM clients WHERE external_id LIKE 'MBRA-DEMO-RECIPIENT-%'",
        "benefit_reviews": "SELECT count(*) FROM cases WHERE case_number LIKE 'MBRA-%'",
        "review_actions": "SELECT count(*) FROM case_tasks WHERE case_id IN (SELECT id FROM cases WHERE case_number LIKE 'MBRA-%')",
        "determinations": "SELECT count(*) FROM cases c JOIN case_statuses s ON s.id=c.status_id WHERE c.case_number LIKE 'MBRA-%' AND s.code IN ('APPROVED','DENIED','CLOSED')",
    }
    with conn.cursor() as cur:
        return {label: (cur.execute(sql), int(cur.fetchone()[0]))[1] for label, sql in queries.items()}


def main() -> int:
    args = parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    random.seed(args.seed)
    Faker.seed(args.seed)
    fake = Faker("en_US")
    with psycopg.connect(args.db_url) as conn:
        if args.reset_demo or args.reset_only:
            reset(conn)
            conn.commit()
        if args.reset_only:
            LOGGER.info("counts %s", counts(conn))
            return 0
        r = refs(conn)
        rows = build_rows(fake, args, r)
        if args.dry_run:
            LOGGER.info("dry-run generated counts %s", {k: len(v) for k, v in rows.items()})
            return 0
        copy_rows(conn, "organizations", ["id", "name", "external_id", "address_line1", "city", "state_province", "postal_code", "country", "phone", "email", "notes", "is_active", "is_deleted", "created_at", "updated_at", "created_by"], rows["agencies"], args.batch_size)
        copy_rows(conn, "clients", ["id", "organization_id", "first_name", "last_name", "middle_name", "date_of_birth", "email", "phone", "address_line1", "city", "state_province", "postal_code", "country", "external_id", "notes", "is_active", "is_deleted", "created_at", "updated_at", "created_by"], rows["recipients"], args.batch_size)
        copy_rows(conn, "cases", ["id", "case_number", "title", "description", "status_id", "priority_id", "type_id", "organization_id", "client_id", "assigned_to_id", "assigned_at", "due_date", "resolved_at", "closed_at", "reopened_count", "is_deleted", "created_at", "updated_at", "created_by", "updated_by"], rows["reviews"], args.batch_size)
        copy_rows(conn, "case_tasks", ["id", "case_id", "title", "description", "status_id", "assigned_to_id", "due_date", "completed_at", "completed_by", "is_deleted", "created_at", "updated_at", "created_by", "updated_by"], rows["actions"], args.batch_size)
        conn.commit()
        LOGGER.info("counts %s", counts(conn))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
