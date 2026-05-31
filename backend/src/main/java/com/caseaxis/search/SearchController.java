package com.caseaxis.search;

import com.caseaxis.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','CASE_WORKER','AUDITOR')")
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    public ResponseEntity<ApiResponse<SearchResultsResponse>> search(
        @RequestParam(defaultValue = "") String q
    ) {
        if (q.isBlank()) {
            return ResponseEntity.ok(ApiResponse.success(SearchResultsResponse.empty()));
        }
        return ResponseEntity.ok(ApiResponse.success(searchService.search(q.trim())));
    }
}
