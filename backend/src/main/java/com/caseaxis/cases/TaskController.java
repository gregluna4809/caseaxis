package com.caseaxis.cases;

import com.caseaxis.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskWorkspaceService taskWorkspaceService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<TaskSummaryResponse>>> listTasks(
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID assignedToId,
            @RequestParam(required = false) UUID caseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueBefore,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueAfter,
            @RequestParam(defaultValue = "false") boolean overdueOnly) {
        return ResponseEntity.ok(ApiResponse.success(
            taskWorkspaceService.listTasks(
                pageable, q, status, assignedToId, caseId, dueBefore, dueAfter, overdueOnly)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskDetailResponse>> getTask(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(taskWorkspaceService.getTask(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CaseTaskResponse>> updateTask(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCaseTaskRequest req,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.success(
            taskWorkspaceService.updateTask(id, req, principal.getUsername())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTask(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails principal) {
        taskWorkspaceService.deleteTask(id, principal.getUsername());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
