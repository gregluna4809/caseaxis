package com.caseaxis.audit;

import com.caseaxis.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cases/{caseId}/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','CASE_WORKER','AUDITOR')")
    public ResponseEntity<ApiResponse<List<AuditEventResponse>>> getCaseAuditEvents(@PathVariable UUID caseId) {
        return ResponseEntity.ok(ApiResponse.success(auditService.getCaseAuditEvents(caseId)));
    }
}
