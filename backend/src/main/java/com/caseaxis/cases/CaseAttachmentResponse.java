package com.caseaxis.cases;

import java.time.Instant;
import java.util.UUID;

public record CaseAttachmentResponse(
    UUID id,
    UUID caseId,
    String originalFilename,
    String storagePath,
    Long fileSizeBytes,
    String mimeType,
    String description,
    UUID createdBy,
    Instant createdAt
) {}
