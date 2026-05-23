package com.caseaxis.organizations;

import com.caseaxis.cases.CaseSummaryResponse;
import com.caseaxis.clients.ClientSummaryResponse;
import com.caseaxis.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService orgService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<OrganizationSummaryResponse>>> listOrganizations(
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(ApiResponse.success(orgService.listOrganizations(pageable, q, active)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrganizationDetailResponse>> getOrganizationById(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(orgService.getOrganizationById(id)));
    }

    @GetMapping("/{id}/clients")
    public ResponseEntity<ApiResponse<Page<ClientSummaryResponse>>> listOrganizationClients(
            @PathVariable UUID id,
            @PageableDefault(size = 20, sort = "lastName", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(orgService.listOrganizationClients(id, pageable)));
    }

    @GetMapping("/{id}/cases")
    public ResponseEntity<ApiResponse<Page<CaseSummaryResponse>>> listOrganizationCases(
            @PathVariable UUID id,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(orgService.listOrganizationCases(id, pageable)));
    }
}
