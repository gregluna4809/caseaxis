package com.caseaxis.cases;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CaseStatusRepository extends JpaRepository<CaseStatus, UUID> {
    Optional<CaseStatus> findByCodeAndActiveTrue(String code);
    Optional<CaseStatus> findByInitialTrueAndActiveTrue();
}
