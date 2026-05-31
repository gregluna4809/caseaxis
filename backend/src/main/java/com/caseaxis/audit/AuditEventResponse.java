package com.caseaxis.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(
    UUID id,
    Instant occurredAt,
    UUID actorId,
    String actorDisplayName,
    String action,
    String eventType,
    String summary
) {
}
