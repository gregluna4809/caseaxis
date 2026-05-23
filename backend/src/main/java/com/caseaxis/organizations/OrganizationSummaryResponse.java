package com.caseaxis.organizations;

import java.util.UUID;

public record OrganizationSummaryResponse(UUID id, String organizationCode, String name) {}
