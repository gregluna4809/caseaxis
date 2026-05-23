package com.caseaxis.cases;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCaseAttachmentRequest(
    @NotBlank @Size(max = 255) String originalFilename,
    @NotBlank @Size(max = 1000) String storagePath,
    Long fileSizeBytes,
    @Size(max = 100) String mimeType,
    @Size(max = 500) String description
) {}
