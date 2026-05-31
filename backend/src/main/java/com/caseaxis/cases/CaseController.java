package com.caseaxis.cases;

import com.caseaxis.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/cases")
@RequiredArgsConstructor
public class CaseController {

    private final CaseService caseService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','CASE_WORKER')")
    public ResponseEntity<ApiResponse<CaseDetailResponse>> createCase(
            @Valid @RequestBody CreateCaseRequest req,
            @AuthenticationPrincipal UserDetails principal) {
        CaseDetailResponse created = caseService.createCase(req, principal.getUsername());
        return ResponseEntity
            .created(URI.create("/api/cases/" + created.id()))
            .body(ApiResponse.success(created));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','CASE_WORKER','AUDITOR')")
    public ResponseEntity<ApiResponse<Page<CaseSummaryResponse>>> listCases(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String type,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.success(
            caseService.listCases(pageable, q, status, priority, type)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','CASE_WORKER','AUDITOR')")
    public ResponseEntity<ApiResponse<CaseDetailResponse>> getCaseById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.success(caseService.getCaseById(id)));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<ApiResponse<CaseDetailResponse>> assignCase(
            @PathVariable UUID id,
            @Valid @RequestBody AssignCaseRequest req,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.success(
            caseService.assignCase(id, req, principal.getUsername())));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','CASE_WORKER')")
    public ResponseEntity<ApiResponse<CaseDetailResponse>> transitionStatus(
            @PathVariable UUID id,
            @Valid @RequestBody TransitionStatusRequest req,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.success(
            caseService.transitionStatus(id, req, principal.getUsername())));
    }

    @PostMapping("/{id}/priority")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<ApiResponse<CaseDetailResponse>> updatePriority(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCasePriorityRequest req,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.success(
            caseService.updatePriority(id, req, principal.getUsername())));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<ApiResponse<CaseDetailResponse>> archiveCase(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.success(
            caseService.archiveCase(id, principal.getUsername())));
    }
}
