package com.caseaxis.cases;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreateCaseTaskRequest(
    @NotBlank @Size(max = 500) String title,
    String description,
    String statusCode,
    UUID assignedToId,
    LocalDate dueDate
) {}
