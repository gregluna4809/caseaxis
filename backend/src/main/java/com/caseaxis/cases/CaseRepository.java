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

    @Query("""
            SELECT new com.caseaxis.cases.CaseDetailProjection(
                c.id,
                c.caseNumber,
                c.title,
                c.description,
                s.code,
                s.displayName,
                p.code,
                p.displayName,
                t.code,
                t.displayName,
                c.organizationId,
                o.organizationCode,
                o.name,
                c.clientId,
                cl.clientNumber,
                cl.firstName,
                cl.lastName,
                c.assignedToId,
                c.assignedAt,
                c.dueDate,
                c.resolvedAt,
                c.closedAt,
                c.reopenedCount,
                c.createdBy,
                c.createdAt,
                c.updatedAt
            )
            FROM CaseRecord c
            JOIN c.status s
            JOIN c.priority p
            JOIN c.type t
            LEFT JOIN Organization o ON o.id = c.organizationId
            LEFT JOIN Client cl ON cl.id = c.clientId
            WHERE c.deleted = false
              AND c.id = :id
            """)
    Optional<CaseDetailProjection> findDetailByIdAndDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT c FROM CaseRecord c " +
                   "JOIN FETCH c.status JOIN FETCH c.priority JOIN FETCH c.type " +
                   "WHERE c.deleted = false",
           countQuery = "SELECT COUNT(c) FROM CaseRecord c WHERE c.deleted = false")
    Page<Case> findAllActive(Pageable pageable);

    @Query(value = """
            SELECT c.* FROM cases c
            JOIN case_statuses s ON s.id = c.status_id
            JOIN case_priorities p ON p.id = c.priority_id
            JOIN case_types t ON t.id = c.type_id
            WHERE c.is_deleted = FALSE
              AND (
                (:searchQuery IS NULL AND :caseNumber IS NULL)
                OR (:searchQuery IS NOT NULL AND c.search_vector @@ to_tsquery('english', :searchQuery))
                OR (:caseNumber IS NOT NULL AND c.case_number = :caseNumber)
              )
              AND (:status IS NULL OR s.code = :status)
              AND (:priority IS NULL OR p.code = :priority)
              AND (:type IS NULL OR t.code = :type)
            ORDER BY c.created_at DESC
            """,
           countQuery = """
            SELECT COUNT(*) FROM cases c
            JOIN case_statuses s ON s.id = c.status_id
            JOIN case_priorities p ON p.id = c.priority_id
            JOIN case_types t ON t.id = c.type_id
            WHERE c.is_deleted = FALSE
              AND (
                (:searchQuery IS NULL AND :caseNumber IS NULL)
                OR (:searchQuery IS NOT NULL AND c.search_vector @@ to_tsquery('english', :searchQuery))
                OR (:caseNumber IS NOT NULL AND c.case_number = :caseNumber)
              )
              AND (:status IS NULL OR s.code = :status)
              AND (:priority IS NULL OR p.code = :priority)
              AND (:type IS NULL OR t.code = :type)
            """,
           nativeQuery = true)
    Page<Case> searchActive(
        @Param("searchQuery") String searchQuery,
        @Param("caseNumber") String caseNumber,
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

    // Per-client counts
    @Query("SELECT COUNT(c) FROM CaseRecord c WHERE c.deleted = false AND c.clientId = :clientId")
    long countByClientId(@Param("clientId") UUID clientId);

    @Query("SELECT COUNT(c) FROM CaseRecord c WHERE c.deleted = false AND c.clientId = :clientId AND c.status.terminal = false")
    long countOpenByClientId(@Param("clientId") UUID clientId);

    @Query("SELECT COUNT(c) FROM CaseRecord c WHERE c.deleted = false AND c.clientId = :clientId AND c.status.code = 'ESCALATED'")
    long countEscalatedByClientId(@Param("clientId") UUID clientId);

    @Query("SELECT COUNT(c) FROM CaseRecord c WHERE c.deleted = false AND c.clientId = :clientId AND c.status.terminal = false AND c.dueDate < :today")
    long countOverdueByClientId(@Param("clientId") UUID clientId, @Param("today") LocalDate today);

    // Per-org counts
    @Query("SELECT COUNT(c) FROM CaseRecord c WHERE c.deleted = false AND c.organizationId = :orgId")
    long countByOrganizationId(@Param("orgId") UUID orgId);

    @Query("SELECT COUNT(c) FROM CaseRecord c WHERE c.deleted = false AND c.organizationId = :orgId AND c.status.terminal = false")
    long countOpenByOrganizationId(@Param("orgId") UUID orgId);

    @Query("SELECT COUNT(c) FROM CaseRecord c WHERE c.deleted = false AND c.organizationId = :orgId AND c.status.code = 'ESCALATED'")
    long countEscalatedByOrganizationId(@Param("orgId") UUID orgId);

    @Query("SELECT COUNT(c) FROM CaseRecord c WHERE c.deleted = false AND c.organizationId = :orgId AND c.status.terminal = false AND c.dueDate < :today")
    long countOverdueByOrganizationId(@Param("orgId") UUID orgId, @Param("today") LocalDate today);

    // Paginated related cases
    @Query(value = """
            SELECT c FROM CaseRecord c
            JOIN FETCH c.status JOIN FETCH c.priority JOIN FETCH c.type
            WHERE c.deleted = false AND c.clientId = :clientId
            """,
           countQuery = "SELECT COUNT(c) FROM CaseRecord c WHERE c.deleted = false AND c.clientId = :clientId")
    Page<Case> findByClientId(@Param("clientId") UUID clientId, Pageable pageable);

    @Query(value = """
            SELECT c FROM CaseRecord c
            JOIN FETCH c.status JOIN FETCH c.priority JOIN FETCH c.type
            WHERE c.deleted = false AND c.organizationId = :orgId
            """,
           countQuery = "SELECT COUNT(c) FROM CaseRecord c WHERE c.deleted = false AND c.organizationId = :orgId")
    Page<Case> findByOrganizationId(@Param("orgId") UUID orgId, Pageable pageable);
}
