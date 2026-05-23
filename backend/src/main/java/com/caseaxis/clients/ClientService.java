package com.caseaxis.clients;

import com.caseaxis.cases.Case;
import com.caseaxis.cases.CaseRepository;
import com.caseaxis.cases.CaseSummaryResponse;
import com.caseaxis.common.exception.ResourceNotFoundException;
import com.caseaxis.organizations.Organization;
import com.caseaxis.organizations.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClientService {

    private final ClientRepository clientRepository;
    private final OrganizationRepository orgRepository;
    private final CaseRepository caseRepository;

    public Page<ClientSummaryResponse> listClients(Pageable pageable, String q, UUID organizationId, Boolean active) {
        String normalizedQ = normalizeText(q);
        Page<Client> clients = normalizedQ == null
            ? clientRepository.filterActive(organizationId, active, pageable)
            : clientRepository.searchActive(normalizedQ, organizationId, active, pageable);

        List<UUID> orgIds = clients.stream()
            .map(Client::getOrganizationId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();

        Map<UUID, Organization> orgMap = orgIds.isEmpty()
            ? Map.of()
            : orgRepository.findAllById(orgIds).stream()
                .collect(Collectors.toMap(Organization::getId, o -> o));

        return clients.map(c -> {
            Organization org = c.getOrganizationId() == null ? null : orgMap.get(c.getOrganizationId());
            return toSummaryResponse(c, org);
        });
    }

    public ClientDetailResponse getClientById(UUID id) {
        Client client = clientRepository.findByIdAndDeletedFalse(id)
            .orElseThrow(() -> new ResourceNotFoundException("Client", id));

        Organization org = client.getOrganizationId() == null
            ? null
            : orgRepository.findById(client.getOrganizationId()).orElse(null);

        LocalDate today = LocalDate.now();
        return new ClientDetailResponse(
            client.getId(),
            client.getClientNumber(),
            client.getLastName() + ", " + client.getFirstName(),
            client.getFirstName(),
            client.getLastName(),
            client.getEmail(),
            client.getPhone(),
            client.getOrganizationId(),
            org == null ? null : org.getOrganizationCode(),
            org == null ? null : org.getName(),
            client.isActive(),
            client.getCreatedAt(),
            client.getUpdatedAt(),
            caseRepository.countByClientId(id),
            caseRepository.countOpenByClientId(id),
            caseRepository.countEscalatedByClientId(id),
            caseRepository.countOverdueByClientId(id, today)
        );
    }

    public Page<CaseSummaryResponse> listClientCases(UUID clientId, Pageable pageable) {
        clientRepository.findByIdAndDeletedFalse(clientId)
            .orElseThrow(() -> new ResourceNotFoundException("Client", clientId));

        return caseRepository.findByClientId(clientId, pageable)
            .map(this::toCaseSummaryResponse);
    }

    private ClientSummaryResponse toSummaryResponse(Client c, Organization org) {
        return new ClientSummaryResponse(
            c.getId(),
            c.getClientNumber(),
            c.getLastName() + ", " + c.getFirstName(),
            c.getEmail(),
            c.getPhone(),
            c.getOrganizationId(),
            org == null ? null : org.getOrganizationCode(),
            org == null ? null : org.getName(),
            c.isActive(),
            c.getCreatedAt()
        );
    }

    private CaseSummaryResponse toCaseSummaryResponse(Case c) {
        return new CaseSummaryResponse(
            c.getId(), c.getCaseNumber(), c.getTitle(),
            c.getStatus().getCode(), c.getStatus().getDisplayName(),
            c.getPriority().getCode(), c.getPriority().getDisplayName(),
            c.getType().getCode(), c.getType().getDisplayName(),
            c.getAssignedToId(), c.getDueDate(), c.getCreatedAt(), c.getUpdatedAt()
        );
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
