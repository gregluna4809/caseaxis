package com.caseaxis.cases;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaseRepository extends JpaRepository<Case, UUID> {

    Optional<Case> findByIdAndDeletedFalse(UUID id);

    @Query(value = "SELECT c FROM CaseRecord c " +
                   "JOIN FETCH c.status JOIN FETCH c.priority JOIN FETCH c.type " +
                   "WHERE c.deleted = false",
           countQuery = "SELECT COUNT(c) FROM CaseRecord c WHERE c.deleted = false")
    Page<Case> findAllActive(Pageable pageable);

    @Query(value = """
            SELECT c FROM CaseRecord c
            JOIN FETCH c.status s
            JOIN FETCH c.priority p
            JOIN FETCH c.type t
            WHERE c.deleted = false
              AND (:q IS NULL
                OR LOWER(c.caseNumber) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(c.title) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(t.displayName) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:status IS NULL OR s.code = :status)
              AND (:priority IS NULL OR p.code = :priority)
              AND (:type IS NULL OR t.code = :type)
            """,
           countQuery = """
            SELECT COUNT(c) FROM CaseRecord c
            JOIN c.status s
            JOIN c.priority p
            JOIN c.type t
            WHERE c.deleted = false
              AND (:q IS NULL
                OR LOWER(c.caseNumber) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(c.title) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(t.displayName) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:status IS NULL OR s.code = :status)
              AND (:priority IS NULL OR p.code = :priority)
              AND (:type IS NULL OR t.code = :type)
            """)
    Page<Case> searchActive(
        @Param("q") String q,
        @Param("status") String status,
        @Param("priority") String priority,
        @Param("type") String type,
        Pageable pageable
    );

    @Query(value = """
            SELECT c FROM CaseRecord c
            JOIN FETCH c.status s
            JOIN FETCH c.priority p
            JOIN FETCH c.type t
            WHERE c.deleted = false
              AND (:status IS NULL OR s.code = :status)
              AND (:priority IS NULL OR p.code = :priority)
              AND (:type IS NULL OR t.code = :type)
            """,
           countQuery = """
            SELECT COUNT(c) FROM CaseRecord c
            JOIN c.status s
            JOIN c.priority p
            JOIN c.type t
            WHERE c.deleted = false
              AND (:status IS NULL OR s.code = :status)
              AND (:priority IS NULL OR p.code = :priority)
              AND (:type IS NULL OR t.code = :type)
            """)
    Page<Case> filterActive(
        @Param("status") String status,
        @Param("priority") String priority,
        @Param("type") String type,
        Pageable pageable
    );

    @Query("SELECT COUNT(c) FROM CaseRecord c WHERE c.deleted = false")
    long countActive();

    @Query("SELECT COUNT(c) FROM CaseRecord c WHERE c.deleted = false AND c.status.terminal = false")
    long countOpen();

    @Query("SELECT COUNT(c) FROM CaseRecord c WHERE c.deleted = false AND c.assignedToId = :userId")
    long countAssignedTo(@Param("userId") UUID userId);

    @Query("SELECT COUNT(c) FROM CaseRecord c WHERE c.deleted = false AND c.status.terminal = false AND c.dueDate < :today")
    long countOverdue(@Param("today") LocalDate today);

    @Query("SELECT COUNT(c) FROM CaseRecord c WHERE c.deleted = false AND c.status.code = 'ESCALATED'")
    long countEscalated();

    @Query("SELECT COUNT(c) FROM CaseRecord c WHERE c.deleted = false AND c.closedAt >= :start AND c.closedAt < :end")
    long countClosedBetween(@Param("start") Instant start, @Param("end") Instant end);

    @Query("""
            SELECT COUNT(c) FROM CaseRecord c
            WHERE c.deleted = false
              AND ((c.closedAt >= :start AND c.closedAt < :end)
                OR (c.resolvedAt >= :start AND c.resolvedAt < :end))
            """)
    long countClosedOrResolvedBetween(@Param("start") Instant start, @Param("end") Instant end);

    @Query("""
            SELECT c FROM CaseRecord c
            JOIN FETCH c.status JOIN FETCH c.priority JOIN FETCH c.type
            WHERE c.deleted = false AND c.assignedToId = :userId
            ORDER BY c.assignedAt DESC NULLS LAST, c.updatedAt DESC
            """)
    List<Case> findRecentlyAssignedTo(@Param("userId") UUID userId, Pageable pageable);

    @Query("""
            SELECT c FROM CaseRecord c
            JOIN FETCH c.status JOIN FETCH c.priority JOIN FETCH c.type
            WHERE c.deleted = false AND c.status.code = 'ESCALATED'
            ORDER BY c.updatedAt DESC
            """)
    List<Case> findLatestEscalated(Pageable pageable);

    @Query("""
            SELECT c FROM CaseRecord c
            JOIN FETCH c.status JOIN FETCH c.priority JOIN FETCH c.type
            WHERE c.deleted = false AND c.status.terminal = false AND c.dueDate < :today
            ORDER BY c.dueDate ASC, c.priority.sortOrder DESC, c.updatedAt DESC
            """)
    List<Case> findTopOverdue(@Param("today") LocalDate today, Pageable pageable);
}
