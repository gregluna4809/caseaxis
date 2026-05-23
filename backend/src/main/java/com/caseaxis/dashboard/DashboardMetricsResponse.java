package com.caseaxis.dashboard;

public record DashboardMetricsResponse(
    long totalCases,
    long openCases,
    long assignedToMe,
    long overdueCases,
    long escalatedCases,
    long closedToday
) {
}
