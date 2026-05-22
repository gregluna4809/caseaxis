package com.caseaxis.cases;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreateCaseRequest(
    @NotBlank @Size(max = 500) String title,
    String description,
    @NotBlank String priorityCode,
    @NotBlank String typeCode,
    UUID organizationId,
    UUID clientId,
    LocalDate dueDate
) {}
