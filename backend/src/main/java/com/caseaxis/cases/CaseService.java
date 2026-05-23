package com.caseaxis.cases;

import com.caseaxis.common.exception.ResourceNotFoundException;
import com.caseaxis.common.util.UuidGenerator;
import com.caseaxis.clients.Client;
import com.caseaxis.clients.ClientRepository;
import com.caseaxis.organizations.Organization;
import com.caseaxis.organizations.OrganizationRepository;
import com.caseaxis.users.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
    private final OrganizationRepository organizationRepository;
    private final ClientRepository clientRepository;
    private final JdbcTemplate jdbcTemplate;

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

        log.debug("Created case {} ({})", saved.getId(), caseNumber);
        return toDetailResponse(saved);
    }

    @Transactional(readOnly = true)
    public CaseDetailResponse getCaseById(UUID caseId) {
        Case c = caseRepository.findByIdAndDeletedFalse(caseId)
            .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));
        return toDetailResponse(c);
    }

    @Transactional(readOnly = true)
    public Page<CaseSummaryResponse> listCases(Pageable pageable) {
        return caseRepository.findAllActive(pageable).map(this::toSummaryResponse);
    }

    @Transactional
    public CaseDetailResponse assignCase(UUID caseId, AssignCaseRequest req, String currentUsername) {
        UUID currentUserId = resolveUserId(currentUsername);

        Case c = caseRepository.findByIdAndDeletedFalse(caseId)
            .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));

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

        log.debug("Assigned case {} to user {}", caseId, req.assigneeId());
        return toDetailResponse(saved);
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

        log.debug("Transitioned case {} from {} to {}", caseId, currentCode, targetCode);
        return toDetailResponse(saved);
    }

    // --- Private helpers ---

    private UUID resolveUserId(String username) {
        return userRepository.findByUsernameAndDeletedFalse(username)
            .orElseThrow(() -> new ResourceNotFoundException("User", username))
            .getId();
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

    private CaseDetailResponse toDetailResponse(Case c) {
        Organization organization = c.getOrganizationId() == null
            ? null
            : organizationRepository.findById(c.getOrganizationId()).orElse(null);
        Client client = c.getClientId() == null
            ? null
            : clientRepository.findById(c.getClientId()).orElse(null);

        return new CaseDetailResponse(
            c.getId(),
            c.getCaseNumber(),
            c.getTitle(),
            c.getDescription(),
            c.getStatus().getCode(),
            c.getStatus().getDisplayName(),
            c.getPriority().getCode(),
            c.getPriority().getDisplayName(),
            c.getType().getCode(),
            c.getType().getDisplayName(),
            c.getOrganizationId(),
            organization == null ? null : organization.getOrganizationCode(),
            organization == null ? null : organization.getName(),
            c.getClientId(),
            client == null ? null : client.getClientNumber(),
            client == null ? null : client.getLastName() + ", " + client.getFirstName(),
            c.getAssignedToId(),
            c.getAssignedAt(),
            c.getDueDate(),
            c.getResolvedAt(),
            c.getClosedAt(),
            c.getReopenedCount(),
            c.getCreatedBy(),
            c.getCreatedAt(),
            c.getUpdatedAt()
        );
    }
}
