package com.caseaxis.cases;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CasePriorityRepository extends JpaRepository<CasePriority, UUID> {
    Optional<CasePriority> findByCodeAndActiveTrue(String code);
}
