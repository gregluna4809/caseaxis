# CaseAxis API Contract

**Version:** Phase 9  
**Base URL:** `http://localhost:8080` (development)  
**Content-Type:** `application/json` (all requests and responses)

---

## Table of Contents

1. [Response Envelope](#response-envelope)
2. [Error Format](#error-format)
3. [Authentication](#authentication)
4. [Health](#health)
5. [Dashboard](#dashboard)
6. [Cases](#cases)
7. [Case Assignments](#case-assignments)
8. [Case Status Transitions](#case-status-transitions)
9. [Case Notes](#case-notes)
10. [Case Tasks](#case-tasks)
11. [Case Attachments](#case-attachments)
12. [Organizations](#organizations)
13. [Clients](#clients)
14. [Reference Data](#reference-data)

---

## Response Envelope

Every response is wrapped in a standard `ApiResponse` envelope.

**Success shape:**
```json
{
  "success": true,
  "data": { ... },
  "timestamp": "2026-05-23T02:37:06.827Z"
}
```

**Error shape:**
```json
{
  "success": false,
  "message": "Human-readable error description",
  "timestamp": "2026-05-23T02:37:06.827Z"
}
```

The `message` field is omitted (`null`) on success responses. The `data` field is omitted on error responses. All timestamps are ISO-8601 UTC (`Instant`).

---

## Error Format

| HTTP Status | Trigger |
|---|---|
| `400 Bad Request` | Bean Validation failure or invalid argument |
| `401 Unauthorized` | Missing, expired, or invalid JWT |
| `404 Not Found` | Resource does not exist or is soft-deleted |
| `409 Conflict` | Invalid state transition |
| `500 Internal Server Error` | Unexpected server error |

**Validation error body (400):**
```json
{
  "success": false,
  "message": "Validation failed",
  "data": {
    "fieldName": "must not be blank"
  },
  "timestamp": "2026-05-23T02:37:06.827Z"
}
```

---

## Authentication

All endpoints except `GET /api/health` and `POST /api/auth/login` require a valid JWT.

**Header format:**
```
Authorization: Bearer <token>
```

Missing or invalid tokens return `401 Unauthorized`.

---

### POST /api/auth/login

Authenticates a user and returns a JWT.

**Auth required:** No

**Request body:**
```json
{
  "username": "admin",
  "password": "admin"
}
```

| Field | Type | Required | Constraints |
|---|---|---|---|
| `username` | string | Yes | not blank |
| `password` | string | Yes | not blank |

**Response — 200 OK:**
```json
{
  "success": true,
  "data": {
    "token": "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6..."
  },
  "timestamp": "2026-05-23T02:30:00.000Z"
}
```

**Error responses:**
- `400` — missing username or password
- `401` — wrong credentials

**Notes:**
- Token lifetime: 24 hours (configurable via `JWT_EXPIRATION_MS` env var)
- Algorithm: HS512
- Claim: `sub` = username

---

## Health

### GET /api/health

Returns the service status. Used for readiness checks.

**Auth required:** No

**Response — 200 OK:**
```json
{
  "success": true,
  "data": {
    "service": "caseaxis-backend",
    "status": "UP"
  },
  "timestamp": "2026-05-23T02:30:00.000Z"
}
```

---

## Dashboard

### GET /api/dashboard/metrics

Returns live operational dashboard counts for the authenticated user.

**Auth required:** Yes

**Response — 200 OK:**
```json
{
  "success": true,
  "data": {
    "totalCases": 75000,
    "openCases": 48120,
    "assignedToMe": 37,
    "overdueCases": 912,
    "escalatedCases": 144,
    "closedToday": 26
  },
  "timestamp": "2026-05-23T02:37:20.622Z"
}
```

**Metric definitions:**

| Field | Definition |
|---|---|
| `totalCases` | non-deleted cases |
| `openCases` | non-deleted cases whose status is not terminal |
| `assignedToMe` | non-deleted cases where `assigned_to_id` is the authenticated user |
| `overdueCases` | non-terminal cases with `due_date` before the current UTC date |
| `escalatedCases` | non-deleted cases with status code `ESCALATED` |
| `closedToday` | non-deleted cases with `closed_at` or `resolved_at` during the current UTC date |

---

### GET /api/dashboard/overview

Returns dashboard metrics plus operational widgets for the authenticated service console user.

**Auth required:** Yes

**Response - 200 OK:**
```json
{
  "success": true,
  "data": {
    "metrics": {
      "totalCases": 75000,
      "openCases": 48120,
      "assignedToMe": 37,
      "overdueCases": 912,
      "escalatedCases": 144,
      "closedToday": 26
    },
    "recentAssignedCases": [],
    "escalationWatch": [],
    "overdueQueue": [],
    "recentActivity": []
  },
  "timestamp": "2026-05-23T02:37:20.622Z"
}
```

**Widget definitions:**

| Field | Definition |
|---|---|
| `recentAssignedCases` | latest 5 non-deleted cases assigned to the authenticated user |
| `escalationWatch` | latest 5 non-deleted cases with status code `ESCALATED` |
| `overdueQueue` | top 5 non-terminal overdue cases, oldest due dates first |
| `recentActivity` | recent notes, status changes, and task updates across non-deleted cases |

---

## Cases

### POST /api/cases

Creates a new case. The case is assigned status `NEW` automatically. A case number in `CA-NNNNNN` format is generated from a global sequence.

**Auth required:** Yes

**Request body:**
```json
{
  "title": "Client disability benefit application",
  "description": "Initial review required before interview.",
  "priorityCode": "HIGH",
  "typeCode": "APPLICATION",
  "organizationId": "019e4d65-ff59-7a8b-927c-d8a6eb4a09cd",
  "clientId": null,
  "dueDate": "2026-06-30"
}
```

| Field | Type | Required | Constraints |
|---|---|---|---|
| `title` | string | Yes | not blank, max 500 chars |
| `description` | string | No | — |
| `priorityCode` | string | Yes | not blank; must match a seeded priority code |
| `typeCode` | string | Yes | not blank; must match a seeded type code |
| `organizationId` | UUID | Conditional | required unless `clientId` is provided |
| `clientId` | UUID | Conditional | required unless `organizationId` is provided |
| `dueDate` | string (ISO date) | No | `YYYY-MM-DD` format |

At least one of `organizationId` or `clientId` must be non-null. A request with both null returns `400`.

**Response — 201 Created:**

`Location` header is set to `/api/cases/{id}`.

```json
{
  "success": true,
  "data": {
    "id": "019e52ae-17da-7942-9752-223e532e487e",
    "caseNumber": "CA-000051",
    "title": "Client disability benefit application",
    "description": "Initial review required before interview.",
    "statusCode": "NEW",
    "statusDisplayName": "New",
    "priorityCode": "HIGH",
    "priorityDisplayName": "High",
    "typeCode": "APPLICATION",
    "typeDisplayName": "Application",
    "organizationId": "019e4d65-ff59-7a8b-927c-d8a6eb4a09cd",
    "organizationCode": "ORG-000654321",
    "organizationName": "Acme Corp",
    "clientId": null,
    "clientNumber": null,
    "clientDisplayName": null,
    "assignedToId": null,
    "assignedAt": null,
    "dueDate": "2026-06-30",
    "resolvedAt": null,
    "closedAt": null,
    "reopenedCount": 0,
    "createdBy": "019e48a4-e1d3-7722-a041-2b1de58e8823",
    "createdAt": "2026-05-23T02:37:06.827Z",
    "updatedAt": "2026-05-23T02:37:06.827Z"
  },
  "timestamp": "2026-05-23T02:37:06.848Z"
}
```

**Error responses:**
- `400` — validation failure, missing subject (no org/client), unknown priority or type code
- `401` — unauthenticated

---

### GET /api/cases

Lists all non-deleted cases. Paginated and sorted.

**Auth required:** Yes

**Query parameters:**

| Param | Type | Default | Notes |
|---|---|---|---|
| `page` | integer | `0` | zero-based page index |
| `size` | integer | `20` | items per page |
| `sort` | string | `createdAt,desc` | field name + direction |
| `q` | string | omitted | optional case number/title/type display search |
| `status` | string | omitted | optional status code filter |
| `priority` | string | omitted | optional priority code filter |
| `type` | string | omitted | optional case type code filter |

**Response — 200 OK:**

Spring `Page` envelope with `content` array of `CaseSummaryResponse`:

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "019e52ae-17da-7942-9752-223e532e487e",
        "caseNumber": "CA-000051",
        "title": "Client disability benefit application",
        "statusCode": "NEW",
        "statusDisplayName": "New",
        "priorityCode": "HIGH",
        "priorityDisplayName": "High",
        "typeCode": "APPLICATION",
        "typeDisplayName": "Application",
        "assignedToId": null,
        "dueDate": "2026-06-30",
        "createdAt": "2026-05-23T02:37:06.827Z",
        "updatedAt": "2026-05-23T02:37:06.827Z"
      }
    ],
    "pageable": {
      "pageNumber": 0,
      "pageSize": 20,
      "sort": { "sorted": true, "empty": false, "unsorted": false }
    },
    "totalElements": 1,
    "totalPages": 1,
    "last": true,
    "first": true,
    "empty": false
  },
  "timestamp": "2026-05-23T02:37:20.622Z"
}
```

**Notes:**
- Summary response omits `description`, `assignedAt`, `resolvedAt`, `closedAt`, `reopenedCount`, `createdBy`
- All non-deleted cases are returned regardless of the caller's role (RBAC filtering is a future phase)

---

### GET /api/cases/{id}

Returns full detail for one case.

**Auth required:** Yes

**Path parameters:**

| Param | Type | Notes |
|---|---|---|
| `id` | UUID | case ID |

**Response — 200 OK:**

Returns full `CaseDetailResponse` (same shape as `POST /api/cases` response `data`).

Client and organization UUID fields remain present for internal API workflows, but UI consumers should display `organizationName` + `organizationCode` and `clientDisplayName` + `clientNumber` instead of raw UUIDs.

**Error responses:**
- `404` — case not found or soft-deleted
- `401` — unauthenticated

---

## Case Assignments

### POST /api/cases/{id}/assign

Assigns a case to a user. If the case already has an active assignment, that assignment is closed (unassigned) before the new one is recorded. The `cases.assigned_to_id` denormalized field is updated atomically.

**Auth required:** Yes

**Path parameters:**

| Param | Type | Notes |
|---|---|---|
| `id` | UUID | case ID |

**Request body:**
```json
{
  "assigneeId": "019e48a4-e1d3-7722-a041-2b1de58e8823",
  "notes": "Assigning to senior worker for initial review."
}
```

| Field | Type | Required | Constraints |
|---|---|---|---|
| `assigneeId` | UUID | Yes | not null; must be a valid user UUID |
| `notes` | string | No | — |

**Response — 200 OK:**

Returns updated `CaseDetailResponse`. `assignedToId` and `assignedAt` reflect the new assignment.

```json
{
  "success": true,
  "data": {
    "id": "019e52ae-17da-7942-9752-223e532e487e",
    "assignedToId": "019e48a4-e1d3-7722-a041-2b1de58e8823",
    "assignedAt": "2026-05-23T02:38:00.000Z",
    ...
  },
  "timestamp": "2026-05-23T02:38:00.012Z"
}
```

**Error responses:**
- `400` — `assigneeId` is null
- `404` — case not found
- `401` — unauthenticated

**Notes:**
- Reassignment is always permitted — there is no status guard on the assignment endpoint
- The database enforces at-most-one-active-assignment via a partial unique index (`uq_case_assignments_one_active`); the service closes the prior assignment before inserting

---

## Case Status Transitions

### POST /api/cases/{id}/status

Transitions a case from its current status to a target status. Only permitted transitions are accepted; invalid transitions return `409`.

**Auth required:** Yes

**Path parameters:**

| Param | Type | Notes |
|---|---|---|
| `id` | UUID | case ID |

**Request body:**
```json
{
  "targetStatusCode": "IN_REVIEW",
  "reason": "All documents received. Beginning review."
}
```

| Field | Type | Required | Constraints |
|---|---|---|---|
| `targetStatusCode` | string | Yes | not blank; must be a valid status code |
| `reason` | string | No | stored in `case_status_history` |

**Permitted transitions:**

| From | Allowed targets |
|---|---|
| `NEW` | `ASSIGNED`, `IN_REVIEW`, `PENDING_INFO`, `ESCALATED` |
| `ASSIGNED` | `IN_REVIEW`, `PENDING_INFO`, `ESCALATED`, `APPROVED`, `DENIED`, `CLOSED` |
| `IN_REVIEW` | `PENDING_INFO`, `ASSIGNED`, `ESCALATED`, `APPROVED`, `DENIED`, `CLOSED` |
| `PENDING_INFO` | `IN_REVIEW`, `ASSIGNED`, `ESCALATED` |
| `ESCALATED` | `IN_REVIEW`, `ASSIGNED`, `PENDING_INFO`, `APPROVED`, `DENIED` |
| `APPROVED` | `REOPENED` |
| `DENIED` | `REOPENED` |
| `CLOSED` | `REOPENED` |
| `REOPENED` | `ASSIGNED`, `IN_REVIEW`, `PENDING_INFO`, `ESCALATED` |

**Response — 200 OK:**

Returns updated `CaseDetailResponse`. `statusCode` and `statusDisplayName` reflect the new status.

When transitioning to `CLOSED`: `closedAt` is set.  
When transitioning to `APPROVED` or `DENIED`: `resolvedAt` is set.  
When transitioning to `REOPENED`: `reopenedCount` is incremented; `resolvedAt` and `closedAt` are cleared.

**Error responses:**
- `400` — `targetStatusCode` is blank or the code is not recognized
- `404` — case not found
- `409` — transition not permitted from the current status
- `401` — unauthenticated

---

## Case Notes

Notes are **immutable evidence records**. The body, case association, and authorship cannot be changed after creation. The database enforces this via a trigger (`trg_case_notes_immutable`). The only permitted post-creation operation is soft deletion.

To correct an erroneous note: soft-delete the original, then create a new note with `supersedesNoteId` pointing to the original. The frontend can display this correction chain.

---

### POST /api/cases/{caseId}/notes

Creates a new note on a case.

**Auth required:** Yes

**Path parameters:**

| Param | Type | Notes |
|---|---|---|
| `caseId` | UUID | must be an existing, non-deleted case |

**Request body:**
```json
{
  "body": "Client called to confirm receipt of notice. Follow-up scheduled.",
  "internal": false,
  "supersedesNoteId": null
}
```

| Field | Type | Required | Constraints |
|---|---|---|---|
| `body` | string | Yes | not blank, max 10,000 chars |
| `internal` | boolean | No | defaults to `false`; internal notes are staff-only |
| `supersedesNoteId` | UUID | No | ID of the note this one corrects; must exist and not be deleted |

**Response — 201 Created:**

`Location` header is set to `/api/cases/{caseId}/notes/{id}`.

```json
{
  "success": true,
  "data": {
    "id": "019e52b1-878b-7692-b808-b01cc3c6308d",
    "caseId": "019e4d65-ff59-7a8b-927c-d8a6eb4a09cd",
    "body": "Client called to confirm receipt of notice. Follow-up scheduled.",
    "internal": false,
    "supersedesNoteId": null,
    "createdBy": "019e48a4-e1d3-7722-a041-2b1de58e8823",
    "createdAt": "2026-05-23T02:37:06.827Z",
    "deleted": false
  },
  "timestamp": "2026-05-23T02:37:06.848Z"
}
```

**Error responses:**
- `400` — body is blank or exceeds 10,000 chars
- `404` — case not found; or `supersedesNoteId` not found
- `401` — unauthenticated

---

### GET /api/cases/{caseId}/notes

Returns all non-deleted notes for a case, ordered newest first.

**Auth required:** Yes

**Path parameters:**

| Param | Type | Notes |
|---|---|---|
| `caseId` | UUID | must be an existing, non-deleted case |

**Response — 200 OK:**

Returns a plain array (not paginated).

```json
{
  "success": true,
  "data": [
    {
      "id": "019e52b1-878b-7692-b808-b01cc3c6308d",
      "caseId": "019e4d65-ff59-7a8b-927c-d8a6eb4a09cd",
      "body": "Client called to confirm receipt of notice. Follow-up scheduled.",
      "internal": false,
      "supersedesNoteId": null,
      "createdBy": "019e48a4-e1d3-7722-a041-2b1de58e8823",
      "createdAt": "2026-05-23T02:37:06.827Z",
      "deleted": false
    }
  ],
  "timestamp": "2026-05-23T02:38:00.622Z"
}
```

**Error responses:**
- `404` — case not found
- `401` — unauthenticated

---

### DELETE /api/cases/{caseId}/notes/{noteId}

Soft-deletes a note. The note is excluded from future list responses but remains in the database for audit purposes.

**Auth required:** Yes

**Path parameters:**

| Param | Type | Notes |
|---|---|---|
| `caseId` | UUID | must be an existing, non-deleted case |
| `noteId` | UUID | must belong to `caseId` and not already be deleted |

**Response — 200 OK:**

```json
{
  "success": true,
  "timestamp": "2026-05-23T02:39:00.000Z"
}
```

**Error responses:**
- `404` — case not found; or note not found or already deleted
- `401` — unauthenticated

---

## Case Tasks

Tasks are **mutable work items** attached to a case. Status, title, description, assignee, and due date can all be updated. Soft deletion is supported.

---

### POST /api/cases/{caseId}/tasks

Creates a new task on a case.

**Auth required:** Yes

**Path parameters:**

| Param | Type | Notes |
|---|---|---|
| `caseId` | UUID | must be an existing, non-deleted case |

**Request body:**
```json
{
  "title": "Request medical records from provider",
  "description": "Contact provider and log confirmation number.",
  "statusCode": "PENDING",
  "assignedToId": null,
  "dueDate": "2026-06-15"
}
```

| Field | Type | Required | Constraints |
|---|---|---|---|
| `title` | string | Yes | not blank, max 500 chars |
| `description` | string | No | — |
| `statusCode` | string | No | defaults to `PENDING`; must be a valid task status code |
| `assignedToId` | UUID | No | — |
| `dueDate` | string (ISO date) | No | `YYYY-MM-DD` |

**Response — 201 Created:**

`Location` header is set to `/api/cases/{caseId}/tasks/{id}`.

```json
{
  "success": true,
  "data": {
    "id": "019e52b1-bd9b-70aa-9ef9-62a6d08dc460",
    "caseId": "019e4d65-ff59-7a8b-927c-d8a6eb4a09cd",
    "title": "Request medical records from provider",
    "description": "Contact provider and log confirmation number.",
    "statusCode": "PENDING",
    "statusDisplayName": "Pending",
    "assignedToId": null,
    "dueDate": "2026-06-15",
    "completedAt": null,
    "completedBy": null,
    "createdBy": "019e48a4-e1d3-7722-a041-2b1de58e8823",
    "createdAt": "2026-05-23T02:37:20.667Z",
    "updatedAt": "2026-05-23T02:37:20.667Z"
  },
  "timestamp": "2026-05-23T02:37:20.673Z"
}
```

**Error responses:**
- `400` — title is blank or exceeds 500 chars; unknown status code
- `404` — case not found
- `401` — unauthenticated

---

### GET /api/cases/{caseId}/tasks

Returns all non-deleted tasks for a case, ordered by creation time ascending.

**Auth required:** Yes

**Path parameters:**

| Param | Type | Notes |
|---|---|---|
| `caseId` | UUID | must be an existing, non-deleted case |

**Response — 200 OK:**

Returns a plain array (not paginated).

```json
{
  "success": true,
  "data": [
    {
      "id": "019e52b1-bd9b-70aa-9ef9-62a6d08dc460",
      "caseId": "019e4d65-ff59-7a8b-927c-d8a6eb4a09cd",
      "title": "Request medical records from provider",
      "description": "Contact provider and log confirmation number.",
      "statusCode": "PENDING",
      "statusDisplayName": "Pending",
      "assignedToId": null,
      "dueDate": "2026-06-15",
      "completedAt": null,
      "completedBy": null,
      "createdBy": "019e48a4-e1d3-7722-a041-2b1de58e8823",
      "createdAt": "2026-05-23T02:37:20.667Z",
      "updatedAt": "2026-05-23T02:37:20.667Z"
    }
  ],
  "timestamp": "2026-05-23T02:38:00.622Z"
}
```

**Error responses:**
- `404` — case not found
- `401` — unauthenticated

---

### GET /api/cases/{caseId}/tasks/{taskId}

Returns detail for one task.

**Auth required:** Yes

**Path parameters:**

| Param | Type | Notes |
|---|---|---|
| `caseId` | UUID | must be an existing, non-deleted case |
| `taskId` | UUID | must belong to `caseId` and not be deleted |

**Response — 200 OK:**

Returns `CaseTaskResponse` (same shape as create response `data`).

**Error responses:**
- `404` — case or task not found
- `401` — unauthenticated

---

### PUT /api/cases/{caseId}/tasks/{taskId}

Updates a task's fields. All fields in the request body are applied; omitted fields default to their zero/null values in the record so all fields must be sent.

**Auth required:** Yes

**Path parameters:**

| Param | Type | Notes |
|---|---|---|
| `caseId` | UUID | must be an existing, non-deleted case |
| `taskId` | UUID | must belong to `caseId` and not be deleted |

**Request body:**
```json
{
  "title": "Request medical records from provider",
  "description": "Called provider — confirmation #4421.",
  "statusCode": "COMPLETED",
  "assignedToId": "019e48a4-e1d3-7722-a041-2b1de58e8823",
  "dueDate": "2026-06-15"
}
```

| Field | Type | Required | Constraints |
|---|---|---|---|
| `title` | string | Yes | not blank, max 500 chars |
| `description` | string | No | — |
| `statusCode` | string | Yes | not blank; must be a valid task status code |
| `assignedToId` | UUID | No | — |
| `dueDate` | string (ISO date) | No | `YYYY-MM-DD` |

**Completion behavior:** When `statusCode` is set to `COMPLETED` for the first time, `completedAt` and `completedBy` are set automatically. Subsequent updates that keep the status as `COMPLETED` do not overwrite the original `completedAt`.

**Response — 200 OK:**

Returns updated `CaseTaskResponse`.

**Error responses:**
- `400` — title blank, unknown status code
- `404` — case or task not found
- `401` — unauthenticated

---

### DELETE /api/cases/{caseId}/tasks/{taskId}

Soft-deletes a task. The task is excluded from future list responses.

**Auth required:** Yes

**Path parameters:**

| Param | Type | Notes |
|---|---|---|
| `caseId` | UUID | must be an existing, non-deleted case |
| `taskId` | UUID | must belong to `caseId` and not already be deleted |

**Response — 200 OK:**

```json
{
  "success": true,
  "timestamp": "2026-05-23T02:39:00.000Z"
}
```

**Error responses:**
- `404` — case or task not found
- `401` — unauthenticated

---

## Case Attachments

Attachments store **metadata only** — the binary content lives on the filesystem or an object store. Metadata is immutable after registration. Soft deletion is supported.

---

### POST /api/cases/{caseId}/attachments

Registers attachment metadata for a file that has already been written to storage.

**Auth required:** Yes

**Path parameters:**

| Param | Type | Notes |
|---|---|---|
| `caseId` | UUID | must be an existing, non-deleted case |

**Request body:**
```json
{
  "originalFilename": "medical-records-2026.pdf",
  "storagePath": "/uploads/2026/05/medical-records-2026.pdf",
  "fileSizeBytes": 204800,
  "mimeType": "application/pdf",
  "description": "Medical records requested from provider."
}
```

| Field | Type | Required | Constraints |
|---|---|---|---|
| `originalFilename` | string | Yes | not blank, max 255 chars |
| `storagePath` | string | Yes | not blank, max 1,000 chars |
| `fileSizeBytes` | long | No | must be ≥ 0 if provided |
| `mimeType` | string | No | max 100 chars |
| `description` | string | No | max 500 chars |

**Response — 201 Created:**

`Location` header is set to `/api/cases/{caseId}/attachments/{id}`.

```json
{
  "success": true,
  "data": {
    "id": "019e52b1-bdcb-7e41-9fdf-ac1126c2ea9f",
    "caseId": "019e4d65-ff59-7a8b-927c-d8a6eb4a09cd",
    "originalFilename": "medical-records-2026.pdf",
    "storagePath": "/uploads/2026/05/medical-records-2026.pdf",
    "fileSizeBytes": 204800,
    "mimeType": "application/pdf",
    "description": "Medical records requested from provider.",
    "createdBy": "019e48a4-e1d3-7722-a041-2b1de58e8823",
    "createdAt": "2026-05-23T02:37:20.715Z"
  },
  "timestamp": "2026-05-23T02:37:20.719Z"
}
```

**Error responses:**
- `400` — filename or storage path blank or exceeds length limit
- `404` — case not found
- `401` — unauthenticated

---

### GET /api/cases/{caseId}/attachments

Returns all non-deleted attachments for a case, ordered newest first.

**Auth required:** Yes

**Path parameters:**

| Param | Type | Notes |
|---|---|---|
| `caseId` | UUID | must be an existing, non-deleted case |

**Response — 200 OK:**

Returns a plain array (not paginated).

```json
{
  "success": true,
  "data": [
    {
      "id": "019e52b1-bdcb-7e41-9fdf-ac1126c2ea9f",
      "caseId": "019e4d65-ff59-7a8b-927c-d8a6eb4a09cd",
      "originalFilename": "medical-records-2026.pdf",
      "storagePath": "/uploads/2026/05/medical-records-2026.pdf",
      "fileSizeBytes": 204800,
      "mimeType": "application/pdf",
      "description": "Medical records requested from provider.",
      "createdBy": "019e48a4-e1d3-7722-a041-2b1de58e8823",
      "createdAt": "2026-05-23T02:37:20.715Z"
    }
  ],
  "timestamp": "2026-05-23T02:38:00.719Z"
}
```

**Error responses:**
- `404` — case not found
- `401` — unauthenticated

---

### DELETE /api/cases/{caseId}/attachments/{attachmentId}

Soft-deletes an attachment record. The physical file is not affected — storage cleanup is the caller's responsibility.

**Auth required:** Yes

**Path parameters:**

| Param | Type | Notes |
|---|---|---|
| `caseId` | UUID | must be an existing, non-deleted case |
| `attachmentId` | UUID | must belong to `caseId` and not already be deleted |

**Response — 200 OK:**

```json
{
  "success": true,
  "timestamp": "2026-05-23T02:39:00.000Z"
}
```

**Error responses:**
- `404` — case or attachment not found
- `401` — unauthenticated

---

## Organizations

All endpoints require a valid JWT `Authorization: Bearer <token>` header.

### GET /api/organizations

List organizations with pagination, optional search, and optional active filter.

**Query parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `page` | integer | No | Zero-based page number (default: 0) |
| `size` | integer | No | Page size (default: 20) |
| `q` | string | No | Search by name or org code (case-insensitive LIKE) |
| `active` | boolean | No | `true` = active only, `false` = inactive only, omit = all |

**Response:** `200 OK` with `Page<OrganizationSummary>`

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "...",
        "organizationCode": "ORG-000000001",
        "name": "Atlantic Risk Partners",
        "active": true,
        "createdAt": "2024-01-01T00:00:00Z",
        "clientCount": 35,
        "caseCount": 146,
        "openCaseCount": 104
      }
    ],
    "totalElements": 250,
    "totalPages": 13,
    "first": true,
    "last": false
  }
}
```

---

### GET /api/organizations/{id}

Get organization detail with full metrics.

**Response:** `200 OK` with `OrganizationDetailResponse`

```json
{
  "success": true,
  "data": {
    "id": "...",
    "organizationCode": "ORG-000000001",
    "name": "Atlantic Risk Partners",
    "phone": "212-555-0100",
    "email": "info@atlanticrisk.example",
    "notes": null,
    "active": true,
    "createdAt": "2024-01-01T00:00:00Z",
    "updatedAt": "2024-06-01T00:00:00Z",
    "clientCount": 35,
    "caseCount": 146,
    "openCaseCount": 104,
    "escalatedCases": 12,
    "overdueCases": 7
  }
}
```

**Error:** `404 Not Found` if organization does not exist or is soft-deleted.

---

### GET /api/organizations/{id}/clients

List active clients for an organization (paginated).

**Query parameters:** `page`, `size` (both default to 0/20 respectively, sort by `lastName`)

**Response:** `200 OK` with `Page<ClientSummaryResponse>` — same shape as `GET /api/clients` content items.

**Error:** `404 Not Found` if organization not found.

---

### GET /api/organizations/{id}/cases

List cases for an organization (paginated).

**Query parameters:** `page`, `size` (default sort: `createdAt DESC`)

**Response:** `200 OK` with `Page<CaseSummaryResponse>` — same shape as `GET /api/cases` content items.

**Error:** `404 Not Found` if organization not found.

---

## Clients

All endpoints require a valid JWT `Authorization: Bearer <token>` header.

### GET /api/clients

List clients with pagination, optional search, optional organization filter, and optional active filter.

**Query parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `page` | integer | No | Zero-based page number (default: 0) |
| `size` | integer | No | Page size (default: 20) |
| `q` | string | No | Search by last name, first name, or client number (case-insensitive LIKE) |
| `organizationId` | UUID | No | Filter by organization |
| `active` | boolean | No | `true` = active only, `false` = inactive only, omit = all |

**Response:** `200 OK` with `Page<ClientSummaryResponse>`

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "...",
        "clientNumber": "CL-000000001",
        "displayName": "Abbott, Darlene",
        "email": "darlene.abbott@example.com",
        "phone": "212-555-0101",
        "organizationId": "...",
        "organizationCode": "ORG-000000001",
        "organizationName": "Atlantic Risk Partners",
        "active": true,
        "createdAt": "2024-01-01T00:00:00Z"
      }
    ],
    "totalElements": 25001,
    "totalPages": 1251,
    "first": true,
    "last": false
  }
}
```

---

### GET /api/clients/{id}

Get client detail with full case statistics.

**Response:** `200 OK` with `ClientDetailResponse`

```json
{
  "success": true,
  "data": {
    "id": "...",
    "clientNumber": "CL-000000001",
    "displayName": "Abbott, Darlene",
    "firstName": "Darlene",
    "lastName": "Abbott",
    "email": "darlene.abbott@example.com",
    "phone": "212-555-0101",
    "organizationId": "...",
    "organizationCode": "ORG-000000001",
    "organizationName": "Atlantic Risk Partners",
    "active": true,
    "createdAt": "2024-01-01T00:00:00Z",
    "updatedAt": "2024-06-01T00:00:00Z",
    "totalCases": 5,
    "openCases": 3,
    "escalatedCases": 1,
    "overdueCases": 0
  }
}
```

**Error:** `404 Not Found` if client does not exist or is soft-deleted.

---

### GET /api/clients/{id}/cases

List cases for a client (paginated).

**Query parameters:** `page`, `size` (default sort: `createdAt DESC`)

**Response:** `200 OK` with `Page<CaseSummaryResponse>` — same shape as `GET /api/cases` content items.

**Error:** `404 Not Found` if client not found.

---

## Reference Data

Reference values are seeded at migration time. Application code looks them up by `code`, never by `id` (IDs differ across environments).

### Organizations

### GET /api/organizations

Returns active, non-deleted organizations for lookup controls, ordered by name.

**Auth required:** Yes

**Response - 200 OK:**
```json
{
  "success": true,
  "data": [
    {
      "id": "019e4d65-ff59-7a8b-927c-d8a6eb4a09cd",
      "organizationCode": "ORG-000654321",
      "name": "Acme Corp"
    }
  ],
  "timestamp": "2026-05-23T02:38:00.622Z"
}
```

`id` is retained for internal write workflows. User-facing displays should use `name` and `organizationCode`.

### Clients

### GET /api/clients

Returns active, non-deleted clients for lookup controls, ordered by last name then first name.

**Auth required:** Yes

**Response - 200 OK:**
```json
{
  "success": true,
  "data": [
    {
      "id": "019e52ae-17da-7942-9752-223e532e487e",
      "clientNumber": "CL-000123456",
      "displayName": "Smith, Jane",
      "organizationId": "019e4d65-ff59-7a8b-927c-d8a6eb4a09cd"
    }
  ],
  "timestamp": "2026-05-23T02:38:00.622Z"
}
```

`id` and `organizationId` are retained for internal write workflows. User-facing displays should use `displayName` and `clientNumber`.

### Case Statuses

| Code | Display Name | Notes |
|---|---|---|
| `NEW` | New | Initial status on case creation |
| `ASSIGNED` | Assigned | — |
| `IN_REVIEW` | In Review | — |
| `PENDING_INFO` | Pending Info | — |
| `ESCALATED` | Escalated | — |
| `APPROVED` | Approved | Terminal — resolves case |
| `DENIED` | Denied | Terminal — resolves case |
| `CLOSED` | Closed | Terminal — closes case |
| `REOPENED` | Reopened | Re-entry point from any terminal status |

### Case Priorities

| Code | Display Name |
|---|---|
| `LOW` | Low |
| `MEDIUM` | Medium |
| `HIGH` | High |
| `CRITICAL` | Critical |

### Case Types

| Code | Display Name |
|---|---|
| `COMPLAINT` | Complaint |
| `APPLICATION` | Application |
| `INQUIRY` | Inquiry |
| `INVESTIGATION` | Investigation |
| `GENERAL` | General |

### Task Statuses

| Code | Display Name | Terminal |
|---|---|---|
| `PENDING` | Pending | No |
| `IN_PROGRESS` | In Progress | No |
| `COMPLETED` | Completed | Yes |
| `CANCELLED` | Cancelled | Yes |

### RBAC Roles

| Code | Display Name | Notes |
|---|---|---|
| `ADMIN` | Administrator | Full system access |
| `SUPERVISOR` | Supervisor | Oversees case workers; can assign and escalate |
| `CASE_WORKER` | Case Worker | Handles assigned cases |
| `AUDITOR` | Auditor | Read-only; cannot create or modify records |

Role-based access control is seeded and modeled in the database but is **not yet enforced at the endpoint level**. All authenticated users currently have full access to all endpoints. Per-role access control is planned for a future phase.

---

## Known Inconsistencies

The following are documented design tradeoffs, not bugs.

1. **List endpoints are not paginated for sub-resources.** `GET /api/cases` returns a Spring `Page` object (with `totalElements`, `totalPages`, etc.). The sub-resource list endpoints — `GET /api/cases/{id}/notes`, `/tasks`, `/attachments` — return plain arrays with no pagination. At the expected scale of notes/tasks/attachments per case this is acceptable; add `Pageable` support when a case can accumulate hundreds of sub-records.

2. **`CaseDetailResponse` omits `updatedBy`.** The response includes `createdBy` but not `updatedBy`. The `updated_by` field exists in the database. This can be added to the response without a schema change.

3. **DELETE responses return `"data": null`.** Soft-delete endpoints return `{"success":true,"data":null,"timestamp":"..."}`. The `data` field is `null` rather than omitted. Jackson omits `null` fields via `@JsonInclude(NON_NULL)`, so in practice `data` is absent from the wire response — but API consumers should not depend on its presence.

4. **No individual GET for notes or attachments.** Notes and attachments can be listed but not fetched by ID. Tasks have a `GET /api/cases/{caseId}/tasks/{taskId}` endpoint; notes and attachments do not. Add if the frontend needs deep-link or direct-lookup behavior.

5. **`statusCode` comparison is case-insensitive on input, case-preserving on output.** The service layer calls `.toUpperCase()` on incoming `priorityCode`, `typeCode`, and `targetStatusCode`. The response always returns the canonical uppercase code stored in the database.
