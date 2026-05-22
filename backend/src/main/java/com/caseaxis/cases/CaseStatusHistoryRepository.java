package com.caseaxis.cases;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CaseStatusHistoryRepository extends JpaRepository<CaseStatusHistory, UUID> {
}
