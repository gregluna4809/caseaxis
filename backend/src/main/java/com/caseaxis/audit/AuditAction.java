package com.caseaxis.audit;

public final class AuditAction {

    public static final String CASE_CREATED = "case_created";
    public static final String CASE_ASSIGNED = "case_assigned";
    public static final String CASE_REASSIGNED = "case_reassigned";
    public static final String CASE_STATUS_CHANGED = "case_status_changed";
    public static final String CASE_PRIORITY_CHANGED = "case_priority_changed";
    public static final String CASE_ARCHIVED = "case_archived";
    public static final String CASE_REOPENED = "case_reopened";

    public static final String TASK_CREATED = "task_created";
    public static final String TASK_UPDATED = "task_updated";
    public static final String TASK_COMPLETED = "task_completed";
    public static final String TASK_DELETED = "task_deleted";

    public static final String NOTE_CREATED = "note_created";
    public static final String NOTE_DELETED = "note_deleted";

    public static final String ATTACHMENT_REGISTERED = "attachment_registered";
    public static final String ATTACHMENT_DELETED = "attachment_deleted";

    public static final String CLIENT_DEACTIVATED = "client_deactivated";
    public static final String ORGANIZATION_DEACTIVATED = "organization_deactivated";

    private AuditAction() {
    }
}
