package com.caseaxis.common.util;

import com.github.f4b6a3.uuid.UuidCreator;

import java.util.UUID;

public final class UuidGenerator {

    private UuidGenerator() {}

    /**
     * Returns a time-ordered UUID v7.
     * Use this for all entity PKs — time-ordering prevents B-tree index fragmentation
     * at the insert rates expected in audit_logs and case_status_history.
     */
    public static UUID generate() {
        return UuidCreator.getTimeOrderedEpoch();
    }
}
