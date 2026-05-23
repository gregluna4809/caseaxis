package com.caseaxis.clients;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientRepository extends JpaRepository<Client, UUID> {

    List<Client> findByDeletedFalseAndActiveTrueOrderByLastNameAscFirstNameAsc();

    Optional<Client> findByIdAndDeletedFalse(UUID id);

    @Query(value = """
            SELECT c FROM Client c
            WHERE c.deleted = false
              AND (:active IS NULL OR c.active = :active)
              AND (:organizationId IS NULL OR c.organizationId = :organizationId)
            """,
           countQuery = """
            SELECT COUNT(c) FROM Client c
            WHERE c.deleted = false
              AND (:active IS NULL OR c.active = :active)
              AND (:organizationId IS NULL OR c.organizationId = :organizationId)
            """)
    Page<Client> filterActive(
        @Param("organizationId") UUID organizationId,
        @Param("active") Boolean active,
        Pageable pageable
    );

    @Query(value = """
            SELECT c FROM Client c
            WHERE c.deleted = false
              AND (:active IS NULL OR c.active = :active)
              AND (:organizationId IS NULL OR c.organizationId = :organizationId)
              AND (LOWER(c.lastName) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(c.firstName) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(c.clientNumber) LIKE LOWER(CONCAT('%', :q, '%')))
            """,
           countQuery = """
            SELECT COUNT(c) FROM Client c
            WHERE c.deleted = false
              AND (:active IS NULL OR c.active = :active)
              AND (:organizationId IS NULL OR c.organizationId = :organizationId)
              AND (LOWER(c.lastName) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(c.firstName) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(c.clientNumber) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Client> searchActive(
        @Param("q") String q,
        @Param("organizationId") UUID organizationId,
        @Param("active") Boolean active,
        Pageable pageable
    );

    @Query("SELECT COUNT(c) FROM Client c WHERE c.deleted = false AND c.active = true AND c.organizationId = :orgId")
    long countActiveByOrganizationId(@Param("orgId") UUID orgId);
}
