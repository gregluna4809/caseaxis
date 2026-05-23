package com.caseaxis.organizations;

import java.time.Instant;
import java.util.UUID;

public record OrganizationDetailResponse(
        UUID id,
        String organizationCode,
        String name,
        String phone,
        String email,
        String notes,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long clientCount,
        long caseCount,
        long openCaseCount,
        long escalatedCases,
        long overdueCases
) {}
