package com.caseaxis.cases;

import com.caseaxis.audit.AuditAction;
import com.caseaxis.audit.AuditService;
import com.caseaxis.common.exception.ResourceNotFoundException;
import com.caseaxis.common.util.UuidGenerator;
import com.caseaxis.users.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CaseService {

    // Valid status transitions. Terminal states (APPROVED, DENIED, CLOSED) may only transition to REOPENED.
    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
        "NEW",          Set.of("ASSIGNED", "IN_REVIEW", "PENDING_INFO", "ESCALATED"),
        "ASSIGNED",     Set.of("IN_REVIEW", "PENDING_INFO", "ESCALATED", "APPROVED", "DENIED", "CLOSED"),
        "IN_REVIEW",    Set.of("PENDING_INFO", "ASSIGNED", "ESCALATED", "APPROVED", "DENIED", "CLOSED"),
        "PENDING_INFO", Set.of("IN_REVIEW", "ASSIGNED", "ESCALATED"),
        "ESCALATED",    Set.of("IN_REVIEW", "ASSIGNED", "PENDING_INFO", "APPROVED", "DENIED"),
        "APPROVED",     Set.of("REOPENED"),
        "DENIED",       Set.of("REOPENED"),
        "CLOSED",       Set.of("REOPENED"),
        "REOPENED",     Set.of("ASSIGNED", "IN_REVIEW", "PENDING_INFO", "ESCALATED")
    );

    private final CaseRepository caseRepository;
    private final CaseStatusRepository statusRepository;
    private final CasePriorityRepository priorityRepository;
    private final CaseTypeRepository typeRepository;
    private final CaseAssignmentRepository assignmentRepository;
    private final CaseStatusHistoryRepository statusHistoryRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AuditService auditService;

    @Transactional
    public CaseDetailResponse createCase(CreateCaseRequest req, String currentUsername) {
        if (req.organizationId() == null && req.clientId() == null) {
            throw new IllegalArgumentException("Case requires at least one of: organizationId, clientId");
        }

        UUID currentUserId = resolveUserId(currentUsername);

        CasePriority priority = priorityRepository.findByCodeAndActiveTrue(req.priorityCode().toUpperCase())
            .orElseThrow(() -> new IllegalArgumentException("Unknown priority code: " + req.priorityCode()));

        CaseType type = typeRepository.findByCodeAndActiveTrue(req.typeCode().toUpperCase())
            .orElseThrow(() -> new IllegalArgumentException("Unknown type code: " + req.typeCode()));

        CaseStatus initialStatus = statusRepository.findByInitialTrueAndActiveTrue()
            .orElseThrow(() -> new IllegalStateException("No initial status configured in database"));

        Long seqVal = jdbcTemplate.queryForObject("SELECT nextval('case_number_seq')", Long.class);
        String caseNumber = String.format("CA-%06d", seqVal);

        Instant now = Instant.now();
        Case newCase = new Case();
        newCase.setId(UuidGenerator.generate());
        newCase.setCaseNumber(caseNumber);
        newCase.setTitle(req.title());
        newCase.setDescription(req.description());
        newCase.setStatus(initialStatus);
        newCase.setPriority(priority);
        newCase.setType(type);
        newCase.setOrganizationId(req.organizationId());
        newCase.setClientId(req.clientId());
        newCase.setDueDate(req.dueDate());
        newCase.setReopenedCount(0);
        newCase.setDeleted(false);
        newCase.setCreatedBy(currentUserId);
        newCase.setCreatedAt(now);
        newCase.setUpdatedAt(now);

        Case saved = caseRepository.save(newCase);

        CaseStatusHistory initialHistory = new CaseStatusHistory();
        initialHistory.setId(UuidGenerator.generate());
        initialHistory.setCaseId(saved.getId());
        initialHistory.setFromStatusId(null);
        initialHistory.setToStatusId(initialStatus.getId());
        initialHistory.setChangedBy(currentUserId);
        initialHistory.setChangedAt(now);
        statusHistoryRepository.save(initialHistory);

        auditService.recordCaseEvent(
            currentUserId,
            saved.getId(),
            AuditAction.CASE_CREATED,
            null,
            AuditService.fields(
                "caseNumber", saved.getCaseNumber(),
                "title", saved.getTitle(),
                "status", initialStatus.getCode(),
                "priority", priority.getCode(),
                "type", type.getCode()
            ),
            null
        );

        log.debug("Created case {} ({})", saved.getId(), caseNumber);
        return toDetailResponse(saved.getId());
    }

    @Transactional(readOnly = true)
    public CaseDetailResponse getCaseById(UUID caseId) {
        return toDetailResponse(caseId);
    }

    @Transactional(readOnly = true)
    public Page<CaseSummaryResponse> listCases(Pageable pageable) {
        return caseRepository.findAllActive(pageable).map(this::toSummaryResponse);
    }

    @Transactional(readOnly = true)
    public Page<CaseSummaryResponse> listCases(
            Pageable pageable,
            String q,
            String status,
            String priority,
            String type) {
        String normalizedQuery = toPrefixTsQuery(q);
        String normalizedStatus = normalizeCode(status);
        String normalizedPriority = normalizeCode(priority);
        String normalizedType = normalizeCode(type);

        if (normalizedQuery == null) {
            return caseRepository.filterActive(
                normalizedStatus,
                normalizedPriority,
                normalizedType,
                pageable
            ).map(this::toSummaryResponse);
        }

        return caseRepository.searchActive(
            normalizedQuery,
            normalizeCaseNumber(q),
            normalizedStatus,
            normalizedPriority,
            normalizedType,
            PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())
        ).map(this::toSummaryResponse);
    }

    @Transactional
    public CaseDetailResponse assignCase(UUID caseId, AssignCaseRequest req, String currentUsername) {
        UUID currentUserId = resolveUserId(currentUsername);

        Case c = caseRepository.findByIdAndDeletedFalse(caseId)
            .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));
        UUID previousAssigneeId = c.getAssignedToId();

        // Close any existing active assignment. flush() before inserting new row so the
        // partial unique index uq_case_assignments_one_active sees the updated state.
        assignmentRepository.findByCaseIdAndUnassignedAtIsNull(caseId).ifPresent(prior -> {
            prior.setUnassignedAt(Instant.now());
            prior.setUnassignedBy(currentUserId);
            assignmentRepository.saveAndFlush(prior);
        });

        Instant now = Instant.now();
        CaseAssignment assignment = new CaseAssignment();
        assignment.setId(UuidGenerator.generate());
        assignment.setCaseId(caseId);
        assignment.setAssigneeId(req.assigneeId());
        assignment.setAssignedBy(currentUserId);
        assignment.setAssignedAt(now);
        assignment.setNotes(req.notes());
        assignmentRepository.save(assignment);

        // Update denormalized fields on case
        c.setAssignedToId(req.assigneeId());
        c.setAssignedAt(now);
        c.setUpdatedBy(currentUserId);
        c.setUpdatedAt(now);
        Case saved = caseRepository.save(c);

        auditService.recordCaseEvent(
            currentUserId,
            caseId,
            previousAssigneeId == null ? AuditAction.CASE_ASSIGNED : AuditAction.CASE_REASSIGNED,
            AuditService.fields("assigneeId", previousAssigneeId),
            AuditService.fields("assigneeId", req.assigneeId()),
            AuditService.fields("notesProvided", req.notes() != null && !req.notes().isBlank())
        );

        log.debug("Assigned case {} to user {}", caseId, req.assigneeId());
        return toDetailResponse(saved.getId());
    }

    @Transactional
    public CaseDetailResponse transitionStatus(UUID caseId, TransitionStatusRequest req, String currentUsername) {
        UUID currentUserId = resolveUserId(currentUsername);

        Case c = caseRepository.findByIdAndDeletedFalse(caseId)
            .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));

        String currentCode = c.getStatus().getCode();
        String targetCode = req.targetStatusCode().toUpperCase();

        Set<String> allowed = ALLOWED_TRANSITIONS.getOrDefault(currentCode, Set.of());
        if (!allowed.contains(targetCode)) {
            throw new IllegalStateException(
                String.format("Transition from %s to %s is not permitted", currentCode, targetCode));
        }

        CaseStatus targetStatus = statusRepository.findByCodeAndActiveTrue(targetCode)
            .orElseThrow(() -> new IllegalArgumentException("Unknown status code: " + targetCode));

        UUID previousStatusId = c.getStatus().getId();
        Instant now = Instant.now();

        c.setStatus(targetStatus);
        c.setUpdatedBy(currentUserId);
        c.setUpdatedAt(now);

        if (targetStatus.isTerminal()) {
            if ("CLOSED".equals(targetCode)) {
                c.setClosedAt(now);
            } else {
                c.setResolvedAt(now);
            }
        } else if ("REOPENED".equals(targetCode)) {
            c.setReopenedCount(c.getReopenedCount() + 1);
            c.setResolvedAt(null);
            c.setClosedAt(null);
        }

        Case saved = caseRepository.save(c);

        CaseStatusHistory history = new CaseStatusHistory();
        history.setId(UuidGenerator.generate());
        history.setCaseId(caseId);
        history.setFromStatusId(previousStatusId);
        history.setToStatusId(targetStatus.getId());
        history.setChangedBy(currentUserId);
        history.setChangedAt(now);
        history.setReason(req.reason());
        statusHistoryRepository.save(history);

        String action = "REOPENED".equals(targetCode)
            ? AuditAction.CASE_REOPENED
            : AuditAction.CASE_STATUS_CHANGED;
        auditService.recordCaseEvent(
            currentUserId,
            caseId,
            action,
            AuditService.fields("status", currentCode),
            AuditService.fields("status", targetCode, "reopenedCount", saved.getReopenedCount()),
            AuditService.fields("reasonProvided", req.reason() != null && !req.reason().isBlank())
        );

        log.debug("Transitioned case {} from {} to {}", caseId, currentCode, targetCode);
        return toDetailResponse(saved.getId());
    }

    @Transactional
    public CaseDetailResponse updatePriority(UUID caseId, UpdateCasePriorityRequest req, String currentUsername) {
        UUID currentUserId = resolveUserId(currentUsername);

        Case c = caseRepository.findByIdAndDeletedFalse(caseId)
            .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));

        String previousPriority = c.getPriority().getCode();
        String targetPriority = req.priorityCode().toUpperCase();
        if (previousPriority.equals(targetPriority)) {
            return toDetailResponse(c.getId());
        }

        CasePriority priority = priorityRepository.findByCodeAndActiveTrue(targetPriority)
            .orElseThrow(() -> new IllegalArgumentException("Unknown priority code: " + targetPriority));

        Instant now = Instant.now();
        c.setPriority(priority);
        c.setUpdatedBy(currentUserId);
        c.setUpdatedAt(now);
        Case saved = caseRepository.save(c);

        auditService.recordCaseEvent(
            currentUserId,
            caseId,
            AuditAction.CASE_PRIORITY_CHANGED,
            AuditService.fields("priority", previousPriority),
            AuditService.fields("priority", targetPriority),
            AuditService.fields("reasonProvided", req.reason() != null && !req.reason().isBlank())
        );

        log.debug("Changed case {} priority from {} to {}", caseId, previousPriority, targetPriority);
        return toDetailResponse(saved.getId());
    }

    @Transactional
    public CaseDetailResponse archiveCase(UUID caseId, String currentUsername) {
        UUID currentUserId = resolveUserId(currentUsername);

        Case c = caseRepository.findByIdAndDeletedFalse(caseId)
            .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));

        if ("CLOSED".equals(c.getStatus().getCode())) {
            return toDetailResponse(c.getId());
        }

        CaseStatus closedStatus = statusRepository.findByCodeAndActiveTrue("CLOSED")
            .orElseThrow(() -> new IllegalStateException("CLOSED status is not configured"));

        UUID previousStatusId = c.getStatus().getId();
        String previousStatusCode = c.getStatus().getCode();
        Instant now = Instant.now();
        c.setStatus(closedStatus);
        c.setClosedAt(now);
        c.setUpdatedBy(currentUserId);
        c.setUpdatedAt(now);
        Case saved = caseRepository.save(c);

        CaseStatusHistory history = new CaseStatusHistory();
        history.setId(UuidGenerator.generate());
        history.setCaseId(caseId);
        history.setFromStatusId(previousStatusId);
        history.setToStatusId(closedStatus.getId());
        history.setChangedBy(currentUserId);
        history.setChangedAt(now);
        history.setReason("Case archived from detail workspace");
        statusHistoryRepository.save(history);

        auditService.recordCaseEvent(
            currentUserId,
            caseId,
            AuditAction.CASE_ARCHIVED,
            AuditService.fields("status", previousStatusCode),
            AuditService.fields("status", "CLOSED"),
            null
        );

        log.debug("Archived case {} as CLOSED", caseId);
        return toDetailResponse(saved.getId());
    }

    // --- Private helpers ---

    private UUID resolveUserId(String username) {
        return userRepository.findByUsernameAndDeletedFalse(username)
            .orElseThrow(() -> new ResourceNotFoundException("User", username))
            .getId();
    }

    public static String toPrefixTsQuery(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = Arrays.stream(value.toLowerCase().split("[^a-z0-9]+"))
            .filter(token -> !token.isBlank())
            .limit(8)
            .map(token -> token + ":*")
            .collect(Collectors.joining(" & "));
        return normalized.isBlank() ? null : normalized;
    }

    public static String normalizeCaseNumber(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        return normalized.matches("CA-\\d{6}") ? normalized : null;
    }

    private String normalizeCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase();
    }

    private CaseSummaryResponse toSummaryResponse(Case c) {
        return new CaseSummaryResponse(
            c.getId(),
            c.getCaseNumber(),
            c.getTitle(),
            c.getStatus().getCode(),
            c.getStatus().getDisplayName(),
            c.getPriority().getCode(),
            c.getPriority().getDisplayName(),
            c.getType().getCode(),
            c.getType().getDisplayName(),
            c.getAssignedToId(),
            c.getDueDate(),
            c.getCreatedAt(),
            c.getUpdatedAt()
        );
    }

    private CaseDetailResponse toDetailResponse(UUID caseId) {
        CaseDetailProjection c = caseRepository.findDetailByIdAndDeletedFalse(caseId)
            .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));
        return toDetailResponse(c);
    }

    static CaseDetailResponse toDetailResponse(CaseDetailProjection c) {
        return new CaseDetailResponse(
            c.id(),
            c.caseNumber(),
            c.title(),
            c.description(),
            c.statusCode(),
            c.statusDisplayName(),
            c.priorityCode(),
            c.priorityDisplayName(),
            c.typeCode(),
            c.typeDisplayName(),
            c.organizationId(),
            c.organizationCode(),
            c.organizationName(),
            c.clientId(),
            c.clientNumber(),
            c.clientLastName() == null || c.clientFirstName() == null
                ? null
                : c.clientLastName() + ", " + c.clientFirstName(),
            c.assignedToId(),
            c.assignedAt(),
            c.dueDate(),
            c.resolvedAt(),
            c.closedAt(),
            c.reopenedCount(),
            c.createdBy(),
            c.createdAt(),
            c.updatedAt()
        );
    }
}
