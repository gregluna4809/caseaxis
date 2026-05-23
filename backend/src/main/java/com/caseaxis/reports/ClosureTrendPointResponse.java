package com.caseaxis.reports;

import java.time.LocalDate;

public record ClosureTrendPointResponse(
    LocalDate date,
    long count
) {}
