package com.caseaxis.dashboard;

import java.util.List;

public record DashboardOverviewResponse(
    DashboardMetricsResponse metrics,
    List<DashboardCaseItemResponse> recentAssignedCases,
    List<DashboardCaseItemResponse> escalationWatch,
    List<DashboardCaseItemResponse> overdueQueue,
    List<DashboardActivityResponse> recentActivity
) {
}
