package com.caseaxis.organizations;

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
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    List<Organization> findByDeletedFalseAndActiveTrueOrderByNameAsc();

    Optional<Organization> findByIdAndDeletedFalse(UUID id);

    @Query(value = """
            SELECT o FROM Organization o
            WHERE o.deleted = false
              AND (:active IS NULL OR o.active = :active)
            """,
           countQuery = """
            SELECT COUNT(o) FROM Organization o
            WHERE o.deleted = false
              AND (:active IS NULL OR o.active = :active)
            """)
    Page<Organization> filterActive(
        @Param("active") Boolean active,
        Pageable pageable
    );

    @Query(value = """
            SELECT o FROM Organization o
            WHERE o.deleted = false
              AND (:active IS NULL OR o.active = :active)
              AND (LOWER(o.name) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(o.organizationCode) LIKE LOWER(CONCAT('%', :q, '%')))
            """,
           countQuery = """
            SELECT COUNT(o) FROM Organization o
            WHERE o.deleted = false
              AND (:active IS NULL OR o.active = :active)
              AND (LOWER(o.name) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(o.organizationCode) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Organization> searchActive(
        @Param("q") String q,
        @Param("active") Boolean active,
        Pageable pageable
    );
}
