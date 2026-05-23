package com.caseaxis.reports;

public record OverdueAgingBucketResponse(
    String bucket,
    int minDays,
    Integer maxDays,
    long count
) {}
