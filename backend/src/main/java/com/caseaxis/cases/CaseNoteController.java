package com.caseaxis.cases;

import com.caseaxis.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cases/{caseId}/notes")
@RequiredArgsConstructor
public class CaseNoteController {

    private final CaseNoteService noteService;

    @PostMapping
    public ResponseEntity<ApiResponse<CaseNoteResponse>> createNote(
            @PathVariable UUID caseId,
            @Valid @RequestBody CreateCaseNoteRequest req,
            @AuthenticationPrincipal UserDetails principal) {
        CaseNoteResponse created = noteService.createNote(caseId, req, principal.getUsername());
        return ResponseEntity
            .created(URI.create("/api/cases/" + caseId + "/notes/" + created.id()))
            .body(ApiResponse.success(created));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CaseNoteResponse>>> listNotes(
            @PathVariable UUID caseId,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.success(noteService.listNotes(caseId)));
    }

    @DeleteMapping("/{noteId}")
    public ResponseEntity<ApiResponse<Void>> deleteNote(
            @PathVariable UUID caseId,
            @PathVariable UUID noteId,
            @AuthenticationPrincipal UserDetails principal) {
        noteService.deleteNote(caseId, noteId, principal.getUsername());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
