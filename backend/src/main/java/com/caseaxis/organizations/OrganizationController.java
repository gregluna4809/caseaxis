package com.caseaxis.organizations;

import com.caseaxis.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationRepository organizationRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<OrganizationSummaryResponse>>> listOrganizations() {
        List<OrganizationSummaryResponse> orgs = organizationRepository
            .findByDeletedFalseAndActiveTrueOrderByNameAsc()
            .stream()
            .map(o -> new OrganizationSummaryResponse(o.getId(), o.getName()))
            .toList();
        return ResponseEntity.ok(ApiResponse.success(orgs));
    }
}
