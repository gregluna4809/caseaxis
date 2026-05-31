package com.caseaxis.reports;

import com.caseaxis.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','AUDITOR')")
public class ReportsController {

    private final ReportsService reportsService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<ReportSummaryResponse>> summary(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam(required = false) LocalDate startDate,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) String caseType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID assigneeId) {
        return ResponseEntity.ok(ApiResponse.success(reportsService.summary(
            filters(startDate, endDate, organizationId, clientId, caseType, status, assigneeId))));
    }

    @GetMapping("/status-distribution")
    public ResponseEntity<ApiResponse<List<DistributionItemResponse>>> statusDistribution(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam(required = false) LocalDate startDate,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) String caseType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID assigneeId) {
        return ResponseEntity.ok(ApiResponse.success(reportsService.statusDistribution(
            filters(startDate, endDate, organizationId, clientId, caseType, status, assigneeId))));
    }

    @GetMapping("/type-distribution")
    public ResponseEntity<ApiResponse<List<DistributionItemResponse>>> typeDistribution(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam(required = false) LocalDate startDate,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) String caseType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID assigneeId) {
        return ResponseEntity.ok(ApiResponse.success(reportsService.typeDistribution(
            filters(startDate, endDate, organizationId, clientId, caseType, status, assigneeId))));
    }

    @GetMapping("/overdue-aging")
    public ResponseEntity<ApiResponse<List<OverdueAgingBucketResponse>>> overdueAging(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam(required = false) LocalDate startDate,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) String caseType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID assigneeId) {
        return ResponseEntity.ok(ApiResponse.success(reportsService.overdueAging(
            filters(startDate, endDate, organizationId, clientId, caseType, status, assigneeId))));
    }

    @GetMapping("/assignee-workload")
    public ResponseEntity<ApiResponse<List<AssigneeWorkloadResponse>>> assigneeWorkload(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam(required = false) LocalDate startDate,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) String caseType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(ApiResponse.success(reportsService.assigneeWorkload(
            filters(startDate, endDate, organizationId, clientId, caseType, status, assigneeId), sort)));
    }

    @GetMapping("/organization-workload")
    public ResponseEntity<ApiResponse<List<OrganizationWorkloadResponse>>> organizationWorkload(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam(required = false) LocalDate startDate,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) String caseType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(ApiResponse.success(reportsService.organizationWorkload(
            filters(startDate, endDate, organizationId, clientId, caseType, status, assigneeId), sort)));
    }

    @GetMapping("/closure-trend")
    public ResponseEntity<ApiResponse<List<ClosureTrendPointResponse>>> closureTrend(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam(required = false) LocalDate startDate,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) String caseType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID assigneeId) {
        return ResponseEntity.ok(ApiResponse.success(reportsService.closureTrend(
            filters(startDate, endDate, organizationId, clientId, caseType, status, assigneeId))));
    }

    @GetMapping(value = "/export/json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<ReportExportResponse>> exportJson(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam(required = false) LocalDate startDate,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) String caseType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID assigneeId) {
        return ResponseEntity.ok(ApiResponse.success(reportsService.exportJson(
            filters(startDate, endDate, organizationId, clientId, caseType, status, assigneeId))));
    }

    @GetMapping(value = "/export/csv", produces = "text/csv")
    public ResponseEntity<String> exportCsv(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam(required = false) LocalDate startDate,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) String caseType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID assigneeId) {
        String csv = reportsService.exportCsv(filters(startDate, endDate, organizationId, clientId, caseType, status, assigneeId));
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=caseaxis-report.csv")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(csv);
    }

    private ReportFilters filters(LocalDate startDate, LocalDate endDate, UUID organizationId,
                                  UUID clientId, String caseType, String status, UUID assigneeId) {
        return new ReportFilters(startDate, endDate, organizationId, clientId, caseType, status, assigneeId);
    }
}
