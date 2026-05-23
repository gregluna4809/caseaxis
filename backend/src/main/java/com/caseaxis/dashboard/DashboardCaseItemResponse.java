package com.caseaxis.dashboard;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DashboardCaseItemResponse(
    UUID id,
    String caseNumber,
    String title,
    String statusCode,
    String statusDisplayName,
    String priorityCode,
    String priorityDisplayName,
    LocalDate dueDate,
    UUID assignedToId,
    Instant updatedAt
) {
}
