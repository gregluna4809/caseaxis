package com.caseaxis.cases;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public interface CaseSummaryProjection {
    UUID getId();
    String getCaseNumber();
    String getTitle();
    String getStatusCode();
    String getStatusDisplayName();
    String getPriorityCode();
    String getPriorityDisplayName();
    String getTypeCode();
    String getTypeDisplayName();
    UUID getAssignedToId();
    String getAssignedToName();
    LocalDate getDueDate();
    Instant getCreatedAt();
    Instant getUpdatedAt();
}
