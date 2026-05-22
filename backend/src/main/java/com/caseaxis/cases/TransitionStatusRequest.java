package com.caseaxis.cases;

import jakarta.validation.constraints.NotBlank;

public record TransitionStatusRequest(
    @NotBlank String targetStatusCode,
    String reason
) {}
