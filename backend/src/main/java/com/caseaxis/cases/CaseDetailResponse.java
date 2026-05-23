package com.caseaxis.cases;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CaseDetailResponse(
    UUID id,
    String caseNumber,
    String title,
    String description,
    String statusCode,
    String statusDisplayName,
    String priorityCode,
    String priorityDisplayName,
    String typeCode,
    String typeDisplayName,
    UUID organizationId,
    String organizationCode,
    String organizationName,
    UUID clientId,
    String clientNumber,
    String clientDisplayName,
    UUID assignedToId,
    Instant assignedAt,
    LocalDate dueDate,
    Instant resolvedAt,
    Instant closedAt,
    int reopenedCount,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt
) {}
