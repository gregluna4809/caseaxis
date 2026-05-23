package com.caseaxis.cases;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CaseTaskResponse(
    UUID id,
    UUID caseId,
    String title,
    String description,
    String statusCode,
    String statusDisplayName,
    UUID assignedToId,
    LocalDate dueDate,
    Instant completedAt,
    UUID completedBy,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt
) {}
