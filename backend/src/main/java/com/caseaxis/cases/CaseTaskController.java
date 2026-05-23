package com.caseaxis.cases;

import com.caseaxis.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cases/{caseId}/tasks")
@RequiredArgsConstructor
public class CaseTaskController {

    private final CaseTaskService taskService;

    @PostMapping
    public ResponseEntity<ApiResponse<CaseTaskResponse>> createTask(
            @PathVariable UUID caseId,
            @Valid @RequestBody CreateCaseTaskRequest req,
            @AuthenticationPrincipal UserDetails principal) {
        CaseTaskResponse created = taskService.createTask(caseId, req, principal.getUsername());
        return ResponseEntity
            .created(URI.create("/api/cases/" + caseId + "/tasks/" + created.id()))
            .body(ApiResponse.success(created));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CaseTaskResponse>>> listTasks(
            @PathVariable UUID caseId,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.success(taskService.listTasks(caseId)));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<ApiResponse<CaseTaskResponse>> getTask(
            @PathVariable UUID caseId,
            @PathVariable UUID taskId,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.success(taskService.getTask(caseId, taskId)));
    }

    @PutMapping("/{taskId}")
    public ResponseEntity<ApiResponse<CaseTaskResponse>> updateTask(
            @PathVariable UUID caseId,
            @PathVariable UUID taskId,
            @Valid @RequestBody UpdateCaseTaskRequest req,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.success(
            taskService.updateTask(caseId, taskId, req, principal.getUsername())));
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<ApiResponse<Void>> deleteTask(
            @PathVariable UUID caseId,
            @PathVariable UUID taskId,
            @AuthenticationPrincipal UserDetails principal) {
        taskService.deleteTask(caseId, taskId, principal.getUsername());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
