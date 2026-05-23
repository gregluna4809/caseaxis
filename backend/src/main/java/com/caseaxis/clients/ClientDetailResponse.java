package com.caseaxis.clients;

import java.time.Instant;
import java.util.UUID;

public record ClientDetailResponse(
        UUID id,
        String clientNumber,
        String displayName,
        String firstName,
        String lastName,
        String email,
        String phone,
        UUID organizationId,
        String organizationCode,
        String organizationName,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long totalCases,
        long openCases,
        long escalatedCases,
        long overdueCases
) {}
