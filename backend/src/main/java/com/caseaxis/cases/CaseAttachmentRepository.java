package com.caseaxis.cases;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaseAttachmentRepository extends JpaRepository<CaseAttachment, UUID> {

    List<CaseAttachment> findByCaseIdAndDeletedFalseOrderByCreatedAtDesc(UUID caseId);

    Optional<CaseAttachment> findByIdAndCaseIdAndDeletedFalse(UUID id, UUID caseId);
}
