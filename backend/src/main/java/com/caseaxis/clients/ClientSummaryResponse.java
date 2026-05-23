package com.caseaxis.clients;

import java.util.UUID;

public record ClientSummaryResponse(UUID id, String displayName, UUID organizationId) {}
