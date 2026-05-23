package com.caseaxis.reports;

import java.util.UUID;

public record AssigneeWorkloadResponse(
    UUID assigneeId,
    String assigneeName,
    long openCases,
    long overdueCases,
    long escalatedCases,
    long closedThisPeriod
) {}
