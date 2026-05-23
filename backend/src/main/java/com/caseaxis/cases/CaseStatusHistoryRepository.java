package com.caseaxis.cases;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface CaseStatusHistoryRepository extends JpaRepository<CaseStatusHistory, UUID> {

    List<CaseStatusHistory> findAllByOrderByChangedAtDesc(Pageable pageable);
}
