package com.caseaxis.cases;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TaskStatusRepository extends JpaRepository<TaskStatus, UUID> {

    Optional<TaskStatus> findByCodeAndActiveTrue(String code);
}
