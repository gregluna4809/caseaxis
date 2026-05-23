package com.caseaxis.cases;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "case_notes")
@Getter
@Setter
public class CaseNote {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "case_id", nullable = false, updatable = false)
    private UUID caseId;

    @Column(name = "body", nullable = false, updatable = false)
    private String body;

    // DB column: is_internal. Only soft-delete fields are mutable after insert.
    @Column(name = "is_internal", nullable = false, updatable = false)
    private boolean internal;

    @Column(name = "supersedes_note_id", updatable = false)
    private UUID supersedesNoteId;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;
}
