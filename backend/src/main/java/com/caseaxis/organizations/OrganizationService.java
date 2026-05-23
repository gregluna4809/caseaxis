package com.caseaxis.organizations;

import com.caseaxis.cases.Case;
import com.caseaxis.cases.CaseRepository;
import com.caseaxis.cases.CaseSummaryResponse;
import com.caseaxis.clients.Client;
import com.caseaxis.clients.ClientRepository;
import com.caseaxis.clients.ClientSummaryResponse;
import com.caseaxis.common.exception.ResourceNotFoundException;
import com.caseaxis.users.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizationService {

    private final OrganizationRepository orgRepository;
    private final ClientRepository clientRepository;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;

    public Page<OrganizationSummaryResponse> listOrganizations(Pageable pageable, String q, Boolean active) {
        String normalizedQ = normalizeText(q);
        Page<Organization> orgs = normalizedQ == null
            ? orgRepository.filterActive(active, pageable)
            : orgRepository.searchActive(normalizedQ, active, pageable);
        return orgs.map(this::toSummaryResponse);
    }

    public OrganizationDetailResponse getOrganizationById(UUID id) {
        Organization org = orgRepository.findByIdAndDeletedFalse(id)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", id));

        LocalDate today = LocalDate.now();
        return new OrganizationDetailResponse(
            org.getId(),
            org.getOrganizationCode(),
            org.getName(),
            org.getPhone(),
            org.getEmail(),
            org.getNotes(),
            org.isActive(),
            org.getCreatedAt(),
            org.getUpdatedAt(),
            clientRepository.countActiveByOrganizationId(id),
            caseRepository.countByOrganizationId(id),
            caseRepository.countOpenByOrganizationId(id),
            caseRepository.countEscalatedByOrganizationId(id),
            caseRepository.countOverdueByOrganizationId(id, today)
        );
    }

    @Transactional
    public OrganizationDetailResponse deactivateOrganization(UUID id, String currentUsername) {
        Organization org = orgRepository.findByIdAndDeletedFalse(id)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", id));

        long activeClients = clientRepository.countActiveByOrganizationId(id);
        long openCases = caseRepository.countOpenByOrganizationId(id);
        if (activeClients > 0 || openCases > 0) {
            throw new IllegalStateException(
                "Organization cannot be deactivated while active clients or open cases are linked to it");
        }

        UUID currentUserId = resolveUserId(currentUsername);
        Instant now = Instant.now();
        org.setActive(false);
        org.setUpdatedBy(currentUserId);
        org.setUpdatedAt(now);
        return getOrganizationById(orgRepository.save(org).getId());
    }

    public Page<ClientSummaryResponse> listOrganizationClients(UUID orgId, Pageable pageable) {
        Organization org = orgRepository.findByIdAndDeletedFalse(orgId)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", orgId));

        return clientRepository.filterActive(orgId, null, pageable)
            .map(c -> toClientSummaryResponse(c, org));
    }

    public Page<CaseSummaryResponse> listOrganizationCases(UUID orgId, Pageable pageable) {
        orgRepository.findByIdAndDeletedFalse(orgId)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", orgId));

        return caseRepository.findByOrganizationId(orgId, pageable)
            .map(this::toCaseSummaryResponse);
    }

    private OrganizationSummaryResponse toSummaryResponse(Organization org) {
        return new OrganizationSummaryResponse(
            org.getId(),
            org.getOrganizationCode(),
            org.getName(),
            org.isActive(),
            org.getCreatedAt(),
            clientRepository.countActiveByOrganizationId(org.getId()),
            caseRepository.countByOrganizationId(org.getId()),
            caseRepository.countOpenByOrganizationId(org.getId())
        );
    }

    private ClientSummaryResponse toClientSummaryResponse(Client c, Organization org) {
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

    private UUID resolveUserId(String username) {
        return userRepository.findByUsernameAndDeletedFalse(username)
            .orElseThrow(() -> new ResourceNotFoundException("User", username))
            .getId();
    }
}
