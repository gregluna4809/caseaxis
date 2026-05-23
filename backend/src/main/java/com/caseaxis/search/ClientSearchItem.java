package com.caseaxis.search;

import java.util.UUID;

public record ClientSearchItem(
    UUID id,
    String clientNumber,
    String displayName,
    String email
) {}
