package com.caseaxis.search;

import com.caseaxis.cases.CaseService;
import com.caseaxis.cases.CaseRepository;
import com.caseaxis.cases.CaseTask;
import com.caseaxis.cases.CaseTaskRepository;
import com.caseaxis.clients.ClientRepository;
import com.caseaxis.organizations.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {

    private static final int LIMIT = 5;

    private final CaseRepository caseRepository;
    private final ClientRepository clientRepository;
    private final OrganizationRepository organizationRepository;
    private final CaseTaskRepository taskRepository;

    public SearchResultsResponse search(String q) {
        var pageable = PageRequest.of(0, LIMIT);
        String caseSearchQuery = CaseService.toPrefixTsQuery(q);
        String caseNumber = CaseService.normalizeCaseNumber(q);

        List<CaseSearchItem> cases = caseSearchQuery == null
            ? List.of()
            : caseRepository
                .searchActiveSummaries(caseSearchQuery, caseNumber, null, null, null, pageable)
                .stream()
                .map(c -> new CaseSearchItem(
                    c.getId(),
                    c.getCaseNumber(),
                    c.getTitle(),
                    c.getStatusCode(),
                    c.getStatusDisplayName()
                ))
                .toList();

        List<ClientSearchItem> clients = clientRepository
            .searchActive(q, null, null, pageable)
            .stream()
            .map(c -> new ClientSearchItem(
                c.getId(),
                c.getClientNumber(),
                c.getFirstName() + " " + c.getLastName(),
                c.getEmail()
            ))
            .toList();

        List<OrganizationSearchItem> organizations = organizationRepository
            .searchActive(q, null, pageable)
            .stream()
            .map(o -> new OrganizationSearchItem(
                o.getId(),
                o.getOrganizationCode(),
                o.getName()
            ))
            .toList();

        List<TaskSearchItem> tasks = taskRepository
            .searchGlobal(q, pageable)
            .stream()
            .map(t -> new TaskSearchItem(
                t.getId(),
                t.getCaseId(),
                t.getTitle(),
                t.getStatus().getCode(),
                t.getStatus().getDisplayName()
            ))
            .toList();

        return new SearchResultsResponse(cases, clients, organizations, tasks);
    }
}
