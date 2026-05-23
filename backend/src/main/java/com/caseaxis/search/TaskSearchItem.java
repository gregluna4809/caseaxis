package com.caseaxis.search;

import java.util.UUID;

public record TaskSearchItem(
    UUID id,
    UUID caseId,
    String title,
    String statusCode,
    String statusDisplayName
) {}
