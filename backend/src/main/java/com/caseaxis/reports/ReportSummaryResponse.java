package com.caseaxis.reports;

public record ReportSummaryResponse(
    long totalCases,
    long openCases,
    long closedCases,
    long overdueCases,
    long escalatedCases,
    Double averageResolutionHours,
    long openTasks,
    long completedTasks
) {}
