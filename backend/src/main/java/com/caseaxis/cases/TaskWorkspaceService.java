package com.caseaxis.cases;

import com.caseaxis.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskWorkspaceService {

    private final CaseTaskRepository taskRepository;
    private final CaseRepository caseRepository;
    private final CaseTaskService caseTaskService;

    public Page<TaskSummaryResponse> listTasks(
            Pageable pageable,
            String q,
            String status,
            UUID assignedToId,
            UUID caseId,
            LocalDate dueBefore,
            LocalDate dueAfter,
            boolean overdueOnly) {

        String normalizedQ = (q != null && !q.isBlank()) ? q.trim() : null;
        LocalDate overdueThreshold = overdueOnly ? LocalDate.now() : null;
        String normalizedStatus = (status != null && !status.isBlank()) ? status.toUpperCase() : null;

        Page<CaseTask> page;
        if (overdueThreshold != null) {
            page = normalizedQ == null
                ? taskRepository.filterWorkspaceOverdue(overdueThreshold, normalizedStatus,
                                                        assignedToId, caseId, dueBefore, dueAfter, pageable)
                : taskRepository.searchWorkspaceOverdue(normalizedQ, overdueThreshold, normalizedStatus,
                                                        assignedToId, caseId, dueBefore, dueAfter, pageable);
        } else {
            page = normalizedQ == null
                ? taskRepository.filterWorkspace(normalizedStatus, assignedToId, caseId,
                                                 dueBefore, dueAfter, null, pageable)
                : taskRepository.searchWorkspace(normalizedQ, normalizedStatus, assignedToId, caseId,
                                                 dueBefore, dueAfter, null, pageable);
        }

        List<UUID> caseIds = page.getContent().stream()
            .map(CaseTask::getCaseId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();

        Map<UUID, Case> caseMap = caseIds.isEmpty() ? Map.of()
            : caseRepository.findAllById(caseIds).stream()
                .collect(Collectors.toMap(Case::getId, c -> c));

        return page.map(task -> {
            Case c = caseMap.get(task.getCaseId());
            return toSummaryResponse(task, c);
        });
    }

    public TaskDetailResponse getTask(UUID id) {
        CaseTask task = taskRepository.findByIdAndDeletedFalse(id)
            .orElseThrow(() -> new ResourceNotFoundException("CaseTask", id));
        Case c = caseRepository.findByIdAndDeletedFalse(task.getCaseId()).orElse(null);
        return toDetailResponse(task, c);
    }

    @Transactional
    public CaseTaskResponse updateTask(UUID taskId, UpdateCaseTaskRequest req, String username) {
        CaseTask task = taskRepository.findByIdAndDeletedFalse(taskId)
            .orElseThrow(() -> new ResourceNotFoundException("CaseTask", taskId));
        return caseTaskService.updateTask(task.getCaseId(), taskId, req, username);
    }

    @Transactional
    public void deleteTask(UUID taskId, String username) {
        CaseTask task = taskRepository.findByIdAndDeletedFalse(taskId)
            .orElseThrow(() -> new ResourceNotFoundException("CaseTask", taskId));
        caseTaskService.deleteTask(task.getCaseId(), taskId, username);
    }

    private TaskSummaryResponse toSummaryResponse(CaseTask task, Case c) {
        return new TaskSummaryResponse(
            task.getId(),
            task.getTitle(),
            task.getDescription(),
            task.getStatus().getCode(),
            task.getStatus().getDisplayName(),
            task.getStatus().isTerminal(),
            task.getDueDate(),
            task.getCompletedAt(),
            task.getCaseId(),
            c == null ? null : c.getCaseNumber(),
            c == null ? null : c.getTitle(),
            task.getAssignedToId() == null ? null : "CaseAxis user",
            task.getCreatedAt(),
            task.getUpdatedAt()
        );
    }

    private TaskDetailResponse toDetailResponse(CaseTask task, Case c) {
        return new TaskDetailResponse(
            task.getId(),
            task.getCaseId(),
            c == null ? null : c.getCaseNumber(),
            c == null ? null : c.getTitle(),
            task.getTitle(),
            task.getDescription(),
            task.getStatus().getCode(),
            task.getStatus().getDisplayName(),
            task.getStatus().isTerminal(),
            task.getDueDate(),
            task.getCompletedAt(),
            task.getAssignedToId() == null ? null : "CaseAxis user",
            task.getCreatedAt(),
            task.getUpdatedAt()
        );
    }
}
