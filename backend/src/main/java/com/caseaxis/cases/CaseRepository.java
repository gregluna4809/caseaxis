package com.caseaxis.cases;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface CaseRepository extends JpaRepository<Case, UUID> {

    Optional<Case> findByIdAndDeletedFalse(UUID id);

    @Query(value = "SELECT c FROM CaseRecord c " +
                   "JOIN FETCH c.status JOIN FETCH c.priority JOIN FETCH c.type " +
                   "WHERE c.deleted = false",
           countQuery = "SELECT COUNT(c) FROM CaseRecord c WHERE c.deleted = false")
    Page<Case> findAllActive(Pageable pageable);
}
