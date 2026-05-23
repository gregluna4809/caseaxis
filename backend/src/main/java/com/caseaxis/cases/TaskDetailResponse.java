package com.caseaxis.cases;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TaskDetailResponse(
    UUID id,
    UUID caseId,
    String caseNumber,
    String caseTitle,
    String title,
    String description,
    String statusCode,
    String statusDisplayName,
    boolean terminal,
    LocalDate dueDate,
    Instant completedAt,
    String assigneeDisplayName,
    Instant createdAt,
    Instant updatedAt
) {}
