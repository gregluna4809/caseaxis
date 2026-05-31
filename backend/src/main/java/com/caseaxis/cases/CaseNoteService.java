package com.caseaxis.cases;

import com.caseaxis.audit.AuditAction;
import com.caseaxis.audit.AuditService;
import com.caseaxis.common.exception.ResourceNotFoundException;
import com.caseaxis.common.util.UuidGenerator;
import com.caseaxis.users.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CaseNoteService {

    private final CaseRepository caseRepository;
    private final CaseNoteRepository noteRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional
    public CaseNoteResponse createNote(UUID caseId, CreateCaseNoteRequest req, String currentUsername) {
        caseRepository.findByIdAndDeletedFalse(caseId)
            .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));

        UUID currentUserId = resolveUserId(currentUsername);

        if (req.supersedesNoteId() != null) {
            noteRepository.findByIdAndDeletedFalse(req.supersedesNoteId())
                .orElseThrow(() -> new ResourceNotFoundException("CaseNote", req.supersedesNoteId()));
        }

        CaseNote note = new CaseNote();
        note.setId(UuidGenerator.generate());
        note.setCaseId(caseId);
        note.setBody(req.body());
        note.setInternal(req.internal());
        note.setSupersedesNoteId(req.supersedesNoteId());
        note.setDeleted(false);
        note.setCreatedBy(currentUserId);
        note.setCreatedAt(Instant.now());

        CaseNote saved = noteRepository.save(note);
        auditService.recordCaseEvent(
            currentUserId,
            caseId,
            AuditAction.NOTE_CREATED,
            null,
            AuditService.fields(
                "noteId", saved.getId(),
                "internal", saved.isInternal(),
                "supersedesNoteId", saved.getSupersedesNoteId()
            ),
            AuditService.fields("internal", saved.isInternal())
        );
        log.debug("Created note {} for case {}", saved.getId(), caseId);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CaseNoteResponse> listNotes(UUID caseId) {
        caseRepository.findByIdAndDeletedFalse(caseId)
            .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));
        return noteRepository.findByCaseIdAndDeletedFalseOrderByCreatedAtDesc(caseId)
            .stream().map(this::toResponse).toList();
    }

    @Transactional
    public void deleteNote(UUID caseId, UUID noteId, String currentUsername) {
        caseRepository.findByIdAndDeletedFalse(caseId)
            .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));

        CaseNote note = noteRepository.findByIdAndCaseIdAndDeletedFalse(noteId, caseId)
            .orElseThrow(() -> new ResourceNotFoundException("CaseNote", noteId));

        UUID currentUserId = resolveUserId(currentUsername);
        note.setDeleted(true);
        note.setDeletedAt(Instant.now());
        note.setDeletedBy(currentUserId);
        noteRepository.save(note);
        auditService.recordCaseEvent(
            currentUserId,
            caseId,
            AuditAction.NOTE_DELETED,
            AuditService.fields("noteId", noteId, "internal", note.isInternal()),
            AuditService.fields("deleted", true),
            AuditService.fields("internal", note.isInternal())
        );
        log.debug("Soft-deleted note {} from case {}", noteId, caseId);
    }

    private UUID resolveUserId(String username) {
        return userRepository.findByUsernameAndDeletedFalse(username)
            .orElseThrow(() -> new ResourceNotFoundException("User", username))
            .getId();
    }

    private CaseNoteResponse toResponse(CaseNote note) {
        return new CaseNoteResponse(
            note.getId(),
            note.getCaseId(),
            note.getBody(),
            note.isInternal(),
            note.getSupersedesNoteId(),
            note.getCreatedBy(),
            note.getCreatedAt(),
            note.isDeleted()
        );
    }
}
