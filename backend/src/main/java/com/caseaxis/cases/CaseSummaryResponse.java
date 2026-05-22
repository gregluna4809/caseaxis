package com.caseaxis.cases;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CaseSummaryResponse(
    UUID id,
    String caseNumber,
    String title,
    String statusCode,
    String statusDisplayName,
    String priorityCode,
    String priorityDisplayName,
    String typeCode,
    String typeDisplayName,
    UUID assignedToId,
    LocalDate dueDate,
    Instant createdAt,
    Instant updatedAt
) {}
