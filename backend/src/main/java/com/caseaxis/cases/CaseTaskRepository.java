package com.caseaxis.cases;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaseTaskRepository extends JpaRepository<CaseTask, UUID> {

    List<CaseTask> findByCaseIdAndDeletedFalseOrderByCreatedAtAsc(UUID caseId);

    Optional<CaseTask> findByIdAndCaseIdAndDeletedFalse(UUID id, UUID caseId);

    List<CaseTask> findByDeletedFalseOrderByUpdatedAtDesc(Pageable pageable);
}
