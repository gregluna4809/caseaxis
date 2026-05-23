package com.caseaxis.search;

import java.util.UUID;

public record OrganizationSearchItem(
    UUID id,
    String organizationCode,
    String name
) {}
