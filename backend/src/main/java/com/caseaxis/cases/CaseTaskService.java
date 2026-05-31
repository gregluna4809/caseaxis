package com.caseaxis.cases;

import com.caseaxis.audit.AuditAction;
import com.caseaxis.audit.AuditService;
import com.caseaxis.common.exception.ResourceNotFoundException;
import com.caseaxis.common.util.UuidGenerator;
import com.caseaxis.users.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CaseTaskService {

    private final CaseRepository caseRepository;
    private final CaseTaskRepository taskRepository;
    private final TaskStatusRepository taskStatusRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional
    public CaseTaskResponse createTask(UUID caseId, CreateCaseTaskRequest req, String currentUsername) {
        caseRepository.findByIdAndDeletedFalse(caseId)
            .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));

        UUID currentUserId = resolveUserId(currentUsername);

        String statusCode = (req.statusCode() != null && !req.statusCode().isBlank())
            ? req.statusCode().toUpperCase()
            : "PENDING";
        TaskStatus status = taskStatusRepository.findByCodeAndActiveTrue(statusCode)
            .orElseThrow(() -> new IllegalArgumentException("Unknown task status code: " + statusCode));

        Instant now = Instant.now();
        CaseTask task = new CaseTask();
        task.setId(UuidGenerator.generate());
        task.setCaseId(caseId);
        task.setTitle(req.title());
        task.setDescription(req.description());
        task.setStatus(status);
        task.setAssignedToId(req.assignedToId());
        task.setDueDate(req.dueDate());
        task.setDeleted(false);
        task.setCreatedBy(currentUserId);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);

        CaseTask saved = taskRepository.save(task);
        auditService.recordCaseEvent(
            currentUserId,
            caseId,
            AuditAction.TASK_CREATED,
            null,
            AuditService.fields(
                "taskId", saved.getId(),
                "status", saved.getStatus().getCode(),
                "assignedToId", saved.getAssignedToId(),
                "dueDate", saved.getDueDate()
            ),
            AuditService.fields("taskTitle", saved.getTitle())
        );
        log.debug("Created task {} for case {}", saved.getId(), caseId);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CaseTaskResponse> listTasks(UUID caseId) {
        caseRepository.findByIdAndDeletedFalse(caseId)
            .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));
        return taskRepository.findByCaseIdAndDeletedFalseOrderByCreatedAtAsc(caseId)
            .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CaseTaskResponse getTask(UUID caseId, UUID taskId) {
        caseRepository.findByIdAndDeletedFalse(caseId)
            .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));
        CaseTask task = taskRepository.findByIdAndCaseIdAndDeletedFalse(taskId, caseId)
            .orElseThrow(() -> new ResourceNotFoundException("CaseTask", taskId));
        return toResponse(task);
    }

    @Transactional
    public CaseTaskResponse updateTask(UUID caseId, UUID taskId, UpdateCaseTaskRequest req, String currentUsername) {
        caseRepository.findByIdAndDeletedFalse(caseId)
            .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));

        CaseTask task = taskRepository.findByIdAndCaseIdAndDeletedFalse(taskId, caseId)
            .orElseThrow(() -> new ResourceNotFoundException("CaseTask", taskId));

        UUID currentUserId = resolveUserId(currentUsername);
        TaskStatus status = taskStatusRepository.findByCodeAndActiveTrue(req.statusCode().toUpperCase())
            .orElseThrow(() -> new IllegalArgumentException("Unknown task status code: " + req.statusCode()));

        String previousStatus = task.getStatus().getCode();
        String previousTitle = task.getTitle();
        UUID previousAssignedToId = task.getAssignedToId();
        java.time.LocalDate previousDueDate = task.getDueDate();

        task.setTitle(req.title());
        task.setDescription(req.description());
        task.setStatus(status);
        task.setAssignedToId(req.assignedToId());
        task.setDueDate(req.dueDate());
        task.setUpdatedBy(currentUserId);
        task.setUpdatedAt(Instant.now());

        // Set completion metadata when transitioning to COMPLETED for the first time.
        if ("COMPLETED".equals(status.getCode()) && task.getCompletedAt() == null) {
            task.setCompletedAt(Instant.now());
            task.setCompletedBy(currentUserId);
        }

        CaseTask saved = taskRepository.save(task);
        String action = "COMPLETED".equals(status.getCode()) && !"COMPLETED".equals(previousStatus)
            ? AuditAction.TASK_COMPLETED
            : AuditAction.TASK_UPDATED;
        auditService.recordCaseEvent(
            currentUserId,
            caseId,
            action,
            AuditService.fields(
                "taskId", taskId,
                "title", previousTitle,
                "status", previousStatus,
                "assignedToId", previousAssignedToId,
                "dueDate", previousDueDate
            ),
            AuditService.fields(
                "taskId", saved.getId(),
                "title", saved.getTitle(),
                "status", saved.getStatus().getCode(),
                "assignedToId", saved.getAssignedToId(),
                "dueDate", saved.getDueDate()
            ),
            AuditService.fields("taskTitle", saved.getTitle())
        );
        log.debug("Updated task {} for case {}", taskId, caseId);
        return toResponse(saved);
    }

    @Transactional
    public void deleteTask(UUID caseId, UUID taskId, String currentUsername) {
        caseRepository.findByIdAndDeletedFalse(caseId)
            .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));

        CaseTask task = taskRepository.findByIdAndCaseIdAndDeletedFalse(taskId, caseId)
            .orElseThrow(() -> new ResourceNotFoundException("CaseTask", taskId));

        UUID currentUserId = resolveUserId(currentUsername);
        Instant now = Instant.now();
        task.setDeleted(true);
        task.setDeletedAt(now);
        task.setDeletedBy(currentUserId);
        task.setUpdatedAt(now);
        task.setUpdatedBy(currentUserId);
        taskRepository.save(task);
        auditService.recordCaseEvent(
            currentUserId,
            caseId,
            AuditAction.TASK_DELETED,
            AuditService.fields("taskId", taskId, "status", task.getStatus().getCode()),
            AuditService.fields("deleted", true),
            AuditService.fields("taskTitle", task.getTitle())
        );
        log.debug("Soft-deleted task {} from case {}", taskId, caseId);
    }

    private UUID resolveUserId(String username) {
        return userRepository.findByUsernameAndDeletedFalse(username)
            .orElseThrow(() -> new ResourceNotFoundException("User", username))
            .getId();
    }

    private CaseTaskResponse toResponse(CaseTask task) {
        return new CaseTaskResponse(
            task.getId(),
            task.getCaseId(),
            task.getTitle(),
            task.getDescription(),
            task.getStatus().getCode(),
            task.getStatus().getDisplayName(),
            task.getAssignedToId(),
            task.getDueDate(),
            task.getCompletedAt(),
            task.getCompletedBy(),
            task.getCreatedBy(),
            task.getCreatedAt(),
            task.getUpdatedAt()
        );
    }
}
