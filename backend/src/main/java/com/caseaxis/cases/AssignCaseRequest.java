package com.caseaxis.cases;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignCaseRequest(
    @NotNull UUID assigneeId,
    String notes
) {}
