package com.caseaxis.reports;

import java.util.List;

public record ReportExportResponse(
    ReportSummaryResponse summary,
    List<DistributionItemResponse> statusDistribution,
    List<DistributionItemResponse> typeDistribution,
    List<OverdueAgingBucketResponse> overdueAging,
    List<AssigneeWorkloadResponse> assigneeWorkload,
    List<OrganizationWorkloadResponse> organizationWorkload,
    List<ClosureTrendPointResponse> closureTrend
) {}
