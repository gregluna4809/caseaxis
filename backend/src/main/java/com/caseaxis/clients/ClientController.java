package com.caseaxis.clients;

import com.caseaxis.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientRepository clientRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ClientSummaryResponse>>> listClients() {
        List<ClientSummaryResponse> clients = clientRepository
            .findByDeletedFalseAndActiveTrueOrderByLastNameAscFirstNameAsc()
            .stream()
            .map(c -> new ClientSummaryResponse(
                c.getId(),
                c.getLastName() + ", " + c.getFirstName(),
                c.getOrganizationId()
            ))
            .toList();
        return ResponseEntity.ok(ApiResponse.success(clients));
    }
}
