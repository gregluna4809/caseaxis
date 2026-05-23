package com.caseaxis.cases;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateCaseNoteRequest(
    @NotBlank @Size(max = 10000) String body,
    boolean internal,
    UUID supersedesNoteId
) {}
