package com.caseaxis.users;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsernameAndDeletedFalse(String username);

    @Query(value = """
        SELECT r.code FROM roles r
        JOIN user_roles ur ON ur.role_id = r.id
        WHERE ur.user_id = :userId
          AND ur.removed_at IS NULL
          AND r.is_active = true
        """, nativeQuery = true)
    List<String> findActiveRoleCodesByUserId(@Param("userId") UUID userId);
}
