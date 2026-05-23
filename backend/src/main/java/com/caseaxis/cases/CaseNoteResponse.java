package com.caseaxis.cases;

import java.time.Instant;
import java.util.UUID;

public record CaseNoteResponse(
    UUID id,
    UUID caseId,
    String body,
    boolean internal,
    UUID supersedesNoteId,
    UUID createdBy,
    Instant createdAt,
    boolean deleted
) {}
