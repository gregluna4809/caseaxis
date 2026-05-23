package com.caseaxis.cases;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TaskSummaryResponse(
    UUID id,
    String title,
    String description,
    String statusCode,
    String statusDisplayName,
    boolean terminal,
    LocalDate dueDate,
    Instant completedAt,
    UUID caseId,
    String caseNumber,
    String caseTitle,
    String assigneeDisplayName,
    Instant createdAt,
    Instant updatedAt
) {}
