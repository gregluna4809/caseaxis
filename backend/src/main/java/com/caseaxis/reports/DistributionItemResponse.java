package com.caseaxis.reports;

public record DistributionItemResponse(
    String code,
    String label,
    long count
) {}
