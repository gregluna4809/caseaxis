package com.caseaxis.cases;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CaseServiceDetailProjectionTest {

    @Test
    void toDetailResponseMapsJoinedOrganizationAndClientMetadata() {
        UUID caseId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();

        CaseDetailResponse response = CaseService.toDetailResponse(new CaseDetailProjection(
            caseId,
            "CA-000123",
            "Projection detail",
            "description",
            "NEW",
            "New",
            "HIGH",
            "High",
            "COMPLAINT",
            "Complaint",
            organizationId,
            "ORG-000000001",
            "Projection Org",
            clientId,
            "CL-000000001",
            "Jane",
            "Doe",
            null,
            null,
            LocalDate.parse("2026-06-30"),
            null,
            null,
            0,
            UUID.randomUUID(),
            Instant.parse("2026-06-19T12:00:00Z"),
            Instant.parse("2026-06-19T12:01:00Z")
        ));

        assertEquals(caseId, response.id());
        assertEquals("ORG-000000001", response.organizationCode());
        assertEquals("Projection Org", response.organizationName());
        assertEquals("CL-000000001", response.clientNumber());
        assertEquals("Doe, Jane", response.clientDisplayName());
    }

    @Test
    void toDetailResponseKeepsNullableOrganizationAndClientFieldsNull() {
        CaseDetailResponse response = CaseService.toDetailResponse(new CaseDetailProjection(
            UUID.randomUUID(),
            "CA-000124",
            "Projection detail without subjects",
            null,
            "NEW",
            "New",
            "LOW",
            "Low",
            "GENERAL",
            "General",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            0,
            UUID.randomUUID(),
            Instant.parse("2026-06-19T12:00:00Z"),
            Instant.parse("2026-06-19T12:01:00Z")
        ));

        assertNull(response.organizationCode());
        assertNull(response.organizationName());
        assertNull(response.clientNumber());
        assertNull(response.clientDisplayName());
    }
}
