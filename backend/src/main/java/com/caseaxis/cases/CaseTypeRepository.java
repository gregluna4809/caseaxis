package com.caseaxis.cases;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CaseTypeRepository extends JpaRepository<CaseType, UUID> {
    Optional<CaseType> findByCodeAndActiveTrue(String code);
}
