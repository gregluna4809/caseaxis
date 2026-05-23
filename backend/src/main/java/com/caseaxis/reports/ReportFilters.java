package com.caseaxis.reports;

import java.time.LocalDate;
import java.util.UUID;

public record ReportFilters(
    LocalDate startDate,
    LocalDate endDate,
    UUID organizationId,
    UUID clientId,
    String caseType,
    String status,
    UUID assigneeId
) {
    public ReportFilters {
        if (caseType != null && caseType.isBlank()) {
            caseType = null;
        }
        if (status != null && status.isBlank()) {
            status = null;
        }
        if (caseType != null) {
            caseType = caseType.toUpperCase();
        }
        if (status != null) {
            status = status.toUpperCase();
        }
    }
}
