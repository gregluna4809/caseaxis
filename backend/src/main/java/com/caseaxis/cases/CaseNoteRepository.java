package com.caseaxis.cases;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaseNoteRepository extends JpaRepository<CaseNote, UUID> {

    List<CaseNote> findByCaseIdAndDeletedFalseOrderByCreatedAtDesc(UUID caseId);

    Optional<CaseNote> findByIdAndCaseIdAndDeletedFalse(UUID id, UUID caseId);

    Optional<CaseNote> findByIdAndDeletedFalse(UUID id);
}
