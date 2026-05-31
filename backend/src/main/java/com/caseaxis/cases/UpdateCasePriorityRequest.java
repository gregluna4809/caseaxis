package com.caseaxis.cases;

import jakarta.validation.constraints.NotBlank;

public record UpdateCasePriorityRequest(
    @NotBlank String priorityCode,
    String reason
) {
}
