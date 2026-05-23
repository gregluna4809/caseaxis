package com.caseaxis.clients;

import com.caseaxis.cases.CaseSummaryResponse;
import com.caseaxis.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ClientSummaryResponse>>> listClients(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(ApiResponse.success(clientService.listClients(pageable, q, organizationId, active)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ClientDetailResponse>> getClientById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(clientService.getClientById(id)));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<ClientDetailResponse>> deactivateClient(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.success(clientService.deactivateClient(id, principal.getUsername())));
    }

    @GetMapping("/{id}/cases")
    public ResponseEntity<ApiResponse<Page<CaseSummaryResponse>>> listClientCases(
            @PathVariable UUID id,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(clientService.listClientCases(id, pageable)));
    }
}
