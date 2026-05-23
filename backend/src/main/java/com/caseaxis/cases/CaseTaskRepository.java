package com.caseaxis.cases;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaseTaskRepository extends JpaRepository<CaseTask, UUID> {

    List<CaseTask> findByCaseIdAndDeletedFalseOrderByCreatedAtAsc(UUID caseId);

    Optional<CaseTask> findByIdAndCaseIdAndDeletedFalse(UUID id, UUID caseId);

    Optional<CaseTask> findByIdAndDeletedFalse(UUID id);

    List<CaseTask> findByDeletedFalseOrderByUpdatedAtDesc(Pageable pageable);

    @Query(value = """
            SELECT t FROM CaseTask t
            WHERE t.deleted = false
              AND (:status IS NULL OR t.status.code = :status)
              AND (:assignedToId IS NULL OR t.assignedToId = :assignedToId)
              AND (:caseId IS NULL OR t.caseId = :caseId)
              AND (:dueBefore IS NULL OR t.dueDate <= :dueBefore)
              AND (:dueAfter IS NULL OR t.dueDate >= :dueAfter)
              AND (:overdueThreshold IS NULL
                    OR (t.dueDate < :overdueThreshold AND t.status.terminal = false))
            """,
           countQuery = """
            SELECT COUNT(t) FROM CaseTask t
            WHERE t.deleted = false
              AND (:status IS NULL OR t.status.code = :status)
              AND (:assignedToId IS NULL OR t.assignedToId = :assignedToId)
              AND (:caseId IS NULL OR t.caseId = :caseId)
              AND (:dueBefore IS NULL OR t.dueDate <= :dueBefore)
              AND (:dueAfter IS NULL OR t.dueDate >= :dueAfter)
              AND (:overdueThreshold IS NULL
                    OR (t.dueDate < :overdueThreshold AND t.status.terminal = false))
            """)
    Page<CaseTask> filterWorkspace(
        @Param("status") String status,
        @Param("assignedToId") UUID assignedToId,
        @Param("caseId") UUID caseId,
        @Param("dueBefore") LocalDate dueBefore,
        @Param("dueAfter") LocalDate dueAfter,
        @Param("overdueThreshold") LocalDate overdueThreshold,
        Pageable pageable
    );

    @Query(value = """
            SELECT t FROM CaseTask t
            WHERE t.deleted = false
              AND (:status IS NULL OR t.status.code = :status)
              AND (:assignedToId IS NULL OR t.assignedToId = :assignedToId)
              AND (:caseId IS NULL OR t.caseId = :caseId)
              AND (:dueBefore IS NULL OR t.dueDate <= :dueBefore)
              AND (:dueAfter IS NULL OR t.dueDate >= :dueAfter)
              AND (:overdueThreshold IS NULL
                    OR (t.dueDate < :overdueThreshold AND t.status.terminal = false))
              AND LOWER(t.title) LIKE LOWER(CONCAT('%', :q, '%'))
            """,
           countQuery = """
            SELECT COUNT(t) FROM CaseTask t
            WHERE t.deleted = false
              AND (:status IS NULL OR t.status.code = :status)
              AND (:assignedToId IS NULL OR t.assignedToId = :assignedToId)
              AND (:caseId IS NULL OR t.caseId = :caseId)
              AND (:dueBefore IS NULL OR t.dueDate <= :dueBefore)
              AND (:dueAfter IS NULL OR t.dueDate >= :dueAfter)
              AND (:overdueThreshold IS NULL
                    OR (t.dueDate < :overdueThreshold AND t.status.terminal = false))
              AND LOWER(t.title) LIKE LOWER(CONCAT('%', :q, '%'))
            """)
    Page<CaseTask> searchWorkspace(
        @Param("q") String q,
        @Param("status") String status,
        @Param("assignedToId") UUID assignedToId,
        @Param("caseId") UUID caseId,
        @Param("dueBefore") LocalDate dueBefore,
        @Param("dueAfter") LocalDate dueAfter,
        @Param("overdueThreshold") LocalDate overdueThreshold,
        Pageable pageable
    );

    // Overdue variants: overdueThreshold is guaranteed non-null, so no IS NULL check
    // (avoids PostgreSQL null type-inference error on date parameters)

    @Query(value = """
            SELECT t FROM CaseTask t
            WHERE t.deleted = false
              AND t.dueDate < :overdueThreshold
              AND t.status.terminal = false
              AND (:status IS NULL OR t.status.code = :status)
              AND (:assignedToId IS NULL OR t.assignedToId = :assignedToId)
              AND (:caseId IS NULL OR t.caseId = :caseId)
              AND (:dueBefore IS NULL OR t.dueDate <= :dueBefore)
              AND (:dueAfter IS NULL OR t.dueDate >= :dueAfter)
            """,
           countQuery = """
            SELECT COUNT(t) FROM CaseTask t
            WHERE t.deleted = false
              AND t.dueDate < :overdueThreshold
              AND t.status.terminal = false
              AND (:status IS NULL OR t.status.code = :status)
              AND (:assignedToId IS NULL OR t.assignedToId = :assignedToId)
              AND (:caseId IS NULL OR t.caseId = :caseId)
              AND (:dueBefore IS NULL OR t.dueDate <= :dueBefore)
              AND (:dueAfter IS NULL OR t.dueDate >= :dueAfter)
            """)
    Page<CaseTask> filterWorkspaceOverdue(
        @Param("overdueThreshold") LocalDate overdueThreshold,
        @Param("status") String status,
        @Param("assignedToId") UUID assignedToId,
        @Param("caseId") UUID caseId,
        @Param("dueBefore") LocalDate dueBefore,
        @Param("dueAfter") LocalDate dueAfter,
        Pageable pageable
    );

    @Query(value = """
            SELECT t FROM CaseTask t
            WHERE t.deleted = false
              AND t.dueDate < :overdueThreshold
              AND t.status.terminal = false
              AND (:status IS NULL OR t.status.code = :status)
              AND (:assignedToId IS NULL OR t.assignedToId = :assignedToId)
              AND (:caseId IS NULL OR t.caseId = :caseId)
              AND (:dueBefore IS NULL OR t.dueDate <= :dueBefore)
              AND (:dueAfter IS NULL OR t.dueDate >= :dueAfter)
              AND LOWER(t.title) LIKE LOWER(CONCAT('%', :q, '%'))
            """,
           countQuery = """
            SELECT COUNT(t) FROM CaseTask t
            WHERE t.deleted = false
              AND t.dueDate < :overdueThreshold
              AND t.status.terminal = false
              AND (:status IS NULL OR t.status.code = :status)
              AND (:assignedToId IS NULL OR t.assignedToId = :assignedToId)
              AND (:caseId IS NULL OR t.caseId = :caseId)
              AND (:dueBefore IS NULL OR t.dueDate <= :dueBefore)
              AND (:dueAfter IS NULL OR t.dueDate >= :dueAfter)
              AND LOWER(t.title) LIKE LOWER(CONCAT('%', :q, '%'))
            """)
    Page<CaseTask> searchWorkspaceOverdue(
        @Param("q") String q,
        @Param("overdueThreshold") LocalDate overdueThreshold,
        @Param("status") String status,
        @Param("assignedToId") UUID assignedToId,
        @Param("caseId") UUID caseId,
        @Param("dueBefore") LocalDate dueBefore,
        @Param("dueAfter") LocalDate dueAfter,
        Pageable pageable
    );
}
