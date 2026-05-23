package com.caseaxis.search;

import java.util.UUID;

public record CaseSearchItem(
    UUID id,
    String caseNumber,
    String title,
    String statusCode,
    String statusDisplayName
) {}
