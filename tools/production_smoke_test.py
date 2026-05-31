#!/usr/bin/env python3
"""
Production smoke test for CaseAxis.

Checks health, authentication, case read paths, one audited write, and the
case audit timeline. Uses environment variables for automation and interactive
prompts for manual runs.
"""

from __future__ import annotations

import argparse
import getpass
import json
import os
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any


DEFAULT_BASE_URL = "https://caseaxis.pulse-forge.com"


@dataclass
class CheckResult:
    name: str
    passed: bool
    detail: str


class SmokeTestError(Exception):
    pass


def main() -> int:
    parser = argparse.ArgumentParser(description="Run a CaseAxis production smoke test.")
    parser.add_argument(
        "--base-url",
        default=os.getenv("CASEAXIS_BASE_URL", DEFAULT_BASE_URL),
        help=f"CaseAxis base URL. Defaults to CASEAXIS_BASE_URL or {DEFAULT_BASE_URL}.",
    )
    args = parser.parse_args()

    base_url = args.base_url.rstrip("/")
    username = os.getenv("CASEAXIS_USERNAME")
    if not username:
        username = input("CaseAxis username: ")

    password = os.getenv("CASEAXIS_PASSWORD")
    if not password:
        password = getpass.getpass("CaseAxis password: ")

    results: list[CheckResult] = []
    token: str | None = None
    case_id: str | None = None

    def run_check(name: str, fn) -> Any:
        try:
            value = fn()
            results.append(CheckResult(name, True, "ok"))
            return value
        except Exception as exc:
            results.append(CheckResult(name, False, str(exc)))
            raise

    try:
        run_check("health", lambda: check_health(base_url))
        token = run_check("authenticate", lambda: authenticate(base_url, username, password))
        first_case = run_check("load cases", lambda: load_first_case(base_url, token))
        case_id = first_case["id"]
        run_check("load case detail", lambda: load_case_detail(base_url, token, case_id))
        note_body = smoke_note_body()
        note_started_at = datetime.now(timezone.utc)
        run_check("create smoke-test note", lambda: create_note(base_url, token, case_id, note_body))
        run_check("verify audit trail", lambda: verify_audit(base_url, token, case_id, note_started_at))
    except Exception:
        print_summary(results)
        return 1

    print_summary(results)
    return 0


def check_health(base_url: str) -> None:
    response = request_json("GET", f"{base_url}/actuator/health")
    if response.get("status") != "UP":
        raise SmokeTestError(f"health status was {response!r}")


def authenticate(base_url: str, username: str, password: str) -> str:
    response = request_json(
        "POST",
        f"{base_url}/api/auth/login",
        body={"username": username, "password": password},
    )
    token = response.get("data", {}).get("token")
    if not token:
        raise SmokeTestError("login response did not include a token")
    return token


def load_first_case(base_url: str, token: str) -> dict[str, Any]:
    response = request_json("GET", f"{base_url}/api/cases?page=0&size=1", token=token)
    content = response.get("data", {}).get("content", [])
    if not content:
        raise SmokeTestError("case list returned no cases")
    case_id = content[0].get("id")
    if not case_id:
        raise SmokeTestError("first case did not include an id")
    return content[0]


def load_case_detail(base_url: str, token: str, case_id: str) -> dict[str, Any]:
    response = request_json("GET", f"{base_url}/api/cases/{case_id}", token=token)
    detail = response.get("data", {})
    if detail.get("id") != case_id:
        raise SmokeTestError("case detail id did not match selected case")
    return detail


def create_note(base_url: str, token: str, case_id: str, body: str) -> dict[str, Any]:
    response = request_json(
        "POST",
        f"{base_url}/api/cases/{case_id}/notes",
        token=token,
        body={"body": body, "internal": True},
    )
    note = response.get("data", {})
    if note.get("caseId") != case_id:
        raise SmokeTestError("created note caseId did not match selected case")
    return note


def verify_audit(base_url: str, token: str, case_id: str, since: datetime) -> None:
    response = request_json("GET", f"{base_url}/api/cases/{case_id}/audit", token=token)
    events = response.get("data", [])
    if not isinstance(events, list):
        raise SmokeTestError("audit response data was not a list")
    for event in events:
        if event.get("action") != "note_created":
            continue
        occurred_at = parse_timestamp(event.get("occurredAt"))
        if occurred_at and occurred_at >= since:
            return
    raise SmokeTestError("audit trail did not include a new note_created event")


def smoke_note_body() -> str:
    timestamp = datetime.now(timezone.utc).isoformat(timespec="seconds")
    return f"CASEAXIS PRODUCTION SMOKE TEST NOTE - safe to ignore - {timestamp}"


def parse_timestamp(value: Any) -> datetime | None:
    if not isinstance(value, str):
        return None
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return None


def request_json(
    method: str,
    url: str,
    token: str | None = None,
    body: dict[str, Any] | None = None,
) -> dict[str, Any]:
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"

    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            raw = response.read().decode("utf-8")
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise SmokeTestError(f"{method} {url} returned HTTP {exc.code}: {safe_error(detail)}") from exc
    except urllib.error.URLError as exc:
        raise SmokeTestError(f"{method} {url} failed: {exc.reason}") from exc
    except json.JSONDecodeError as exc:
        raise SmokeTestError(f"{method} {url} returned invalid JSON") from exc


def safe_error(detail: str) -> str:
    if not detail:
        return ""
    try:
        parsed = json.loads(detail)
        message = parsed.get("message")
        return str(message) if message else "request failed"
    except json.JSONDecodeError:
        return detail[:200]


def print_summary(results: list[CheckResult]) -> None:
    print()
    print("CaseAxis smoke test summary")
    print("---------------------------")
    for result in results:
        status = "PASS" if result.passed else "FAIL"
        print(f"{status} {result.name}: {result.detail}")
    if all(result.passed for result in results):
        print("PASS overall")
    else:
        print("FAIL overall")


if __name__ == "__main__":
    sys.exit(main())
