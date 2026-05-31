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
public class CaseAttachmentService {

    private final CaseRepository caseRepository;
    private final CaseAttachmentRepository attachmentRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional
    public CaseAttachmentResponse registerAttachment(UUID caseId, CreateCaseAttachmentRequest req, String currentUsername) {
        caseRepository.findByIdAndDeletedFalse(caseId)
            .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));

        UUID currentUserId = resolveUserId(currentUsername);

        CaseAttachment attachment = new CaseAttachment();
        attachment.setId(UuidGenerator.generate());
        attachment.setCaseId(caseId);
        attachment.setOriginalFilename(req.originalFilename());
        attachment.setStoragePath(req.storagePath());
        attachment.setFileSizeBytes(req.fileSizeBytes());
        attachment.setMimeType(req.mimeType());
        attachment.setDescription(req.description());
        attachment.setDeleted(false);
        attachment.setCreatedBy(currentUserId);
        attachment.setCreatedAt(Instant.now());

        CaseAttachment saved = attachmentRepository.save(attachment);
        auditService.recordCaseEvent(
            currentUserId,
            caseId,
            AuditAction.ATTACHMENT_REGISTERED,
            null,
            AuditService.fields(
                "attachmentId", saved.getId(),
                "filename", saved.getOriginalFilename(),
                "fileSizeBytes", saved.getFileSizeBytes(),
                "mimeType", saved.getMimeType()
            ),
            AuditService.fields("filename", saved.getOriginalFilename())
        );
        log.debug("Registered attachment {} for case {}", saved.getId(), caseId);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CaseAttachmentResponse> listAttachments(UUID caseId) {
        caseRepository.findByIdAndDeletedFalse(caseId)
            .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));
        return attachmentRepository.findByCaseIdAndDeletedFalseOrderByCreatedAtDesc(caseId)
            .stream().map(this::toResponse).toList();
    }

    @Transactional
    public void deleteAttachment(UUID caseId, UUID attachmentId, String currentUsername) {
        caseRepository.findByIdAndDeletedFalse(caseId)
            .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));

        CaseAttachment attachment = attachmentRepository.findByIdAndCaseIdAndDeletedFalse(attachmentId, caseId)
            .orElseThrow(() -> new ResourceNotFoundException("CaseAttachment", attachmentId));

        UUID currentUserId = resolveUserId(currentUsername);
        attachment.setDeleted(true);
        attachment.setDeletedAt(Instant.now());
        attachment.setDeletedBy(currentUserId);
        attachmentRepository.save(attachment);
        auditService.recordCaseEvent(
            currentUserId,
            caseId,
            AuditAction.ATTACHMENT_DELETED,
            AuditService.fields("attachmentId", attachmentId, "filename", attachment.getOriginalFilename()),
            AuditService.fields("deleted", true),
            AuditService.fields("filename", attachment.getOriginalFilename())
        );
        log.debug("Soft-deleted attachment {} from case {}", attachmentId, caseId);
    }

    private UUID resolveUserId(String username) {
        return userRepository.findByUsernameAndDeletedFalse(username)
            .orElseThrow(() -> new ResourceNotFoundException("User", username))
            .getId();
    }

    private CaseAttachmentResponse toResponse(CaseAttachment attachment) {
        return new CaseAttachmentResponse(
            attachment.getId(),
            attachment.getCaseId(),
            attachment.getOriginalFilename(),
            attachment.getStoragePath(),
            attachment.getFileSizeBytes(),
            attachment.getMimeType(),
            attachment.getDescription(),
            attachment.getCreatedBy(),
            attachment.getCreatedAt()
        );
    }
}
