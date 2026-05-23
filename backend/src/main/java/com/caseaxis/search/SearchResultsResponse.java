package com.caseaxis.search;

import java.util.List;

public record SearchResultsResponse(
    List<CaseSearchItem> cases,
    List<ClientSearchItem> clients,
    List<OrganizationSearchItem> organizations,
    List<TaskSearchItem> tasks
) {
    static SearchResultsResponse empty() {
        return new SearchResultsResponse(List.of(), List.of(), List.of(), List.of());
    }
}
