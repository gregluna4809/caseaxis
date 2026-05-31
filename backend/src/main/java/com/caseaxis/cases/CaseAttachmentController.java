package com.caseaxis.cases;

import com.caseaxis.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cases/{caseId}/attachments")
@RequiredArgsConstructor
public class CaseAttachmentController {

    private final CaseAttachmentService attachmentService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','CASE_WORKER')")
    public ResponseEntity<ApiResponse<CaseAttachmentResponse>> registerAttachment(
            @PathVariable UUID caseId,
            @Valid @RequestBody CreateCaseAttachmentRequest req,
            @AuthenticationPrincipal UserDetails principal) {
        CaseAttachmentResponse created = attachmentService.registerAttachment(caseId, req, principal.getUsername());
        return ResponseEntity
            .created(URI.create("/api/cases/" + caseId + "/attachments/" + created.id()))
            .body(ApiResponse.success(created));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','CASE_WORKER','AUDITOR')")
    public ResponseEntity<ApiResponse<List<CaseAttachmentResponse>>> listAttachments(
            @PathVariable UUID caseId,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.success(attachmentService.listAttachments(caseId)));
    }

    @DeleteMapping("/{attachmentId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','CASE_WORKER')")
    public ResponseEntity<ApiResponse<Void>> deleteAttachment(
            @PathVariable UUID caseId,
            @PathVariable UUID attachmentId,
            @AuthenticationPrincipal UserDetails principal) {
        attachmentService.deleteAttachment(caseId, attachmentId, principal.getUsername());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
