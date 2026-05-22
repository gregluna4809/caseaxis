package com.caseaxis.cases;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CaseAssignmentRepository extends JpaRepository<CaseAssignment, UUID> {
    Optional<CaseAssignment> findByCaseIdAndUnassignedAtIsNull(UUID caseId);
}
