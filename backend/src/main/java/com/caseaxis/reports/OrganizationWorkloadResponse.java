package com.caseaxis.reports;

import java.util.UUID;

public record OrganizationWorkloadResponse(
    UUID organizationId,
    String organizationCode,
    String organizationName,
    long totalCases,
    long openCases,
    long escalatedCases
) {}
