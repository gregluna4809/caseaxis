package com.caseaxis.dashboard;

import java.time.Instant;
import java.util.UUID;

public record DashboardActivityResponse(
    String type,
    UUID caseId,
    String caseNumber,
    String caseTitle,
    String summary,
    UUID actorId,
    Instant occurredAt
) {
}
