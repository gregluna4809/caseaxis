package com.caseaxis.dashboard;

import com.caseaxis.cases.Case;
import com.caseaxis.cases.CaseNote;
import com.caseaxis.cases.CaseNoteRepository;
import com.caseaxis.cases.CaseRepository;
import com.caseaxis.cases.CaseStatusHistory;
import com.caseaxis.cases.CaseStatusHistoryRepository;
import com.caseaxis.cases.CaseTask;
import com.caseaxis.cases.CaseTaskRepository;
import com.caseaxis.common.exception.ResourceNotFoundException;
import com.caseaxis.users.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final CaseRepository caseRepository;
    private final CaseNoteRepository caseNoteRepository;
    private final CaseStatusHistoryRepository statusHistoryRepository;
    private final CaseTaskRepository caseTaskRepository;
    private final UserRepository userRepository;
    private final Clock clock = Clock.systemUTC();

    @Transactional(readOnly = true)
    public DashboardMetricsResponse getMetrics(String username) {
        UUID userId = resolveUserId(username);

        LocalDate today = LocalDate.now(clock);
        var startOfDay = today.atStartOfDay().toInstant(ZoneOffset.UTC);
        var startOfTomorrow = today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        return new DashboardMetricsResponse(
            caseRepository.countActive(),
            caseRepository.countOpen(),
            caseRepository.countAssignedTo(userId),
            caseRepository.countOverdue(today),
            caseRepository.countEscalated(),
            caseRepository.countClosedOrResolvedBetween(startOfDay, startOfTomorrow)
        );
    }

    @Transactional(readOnly = true)
    public DashboardOverviewResponse getOverview(String username) {
        UUID userId = resolveUserId(username);
        LocalDate today = LocalDate.now(clock);
        PageRequest topFive = PageRequest.of(0, 5);

        return new DashboardOverviewResponse(
            getMetrics(username),
            caseRepository.findRecentlyAssignedTo(userId, topFive).stream().map(this::toCaseItem).toList(),
            caseRepository.findLatestEscalated(topFive).stream().map(this::toCaseItem).toList(),
            caseRepository.findTopOverdue(today, topFive).stream().map(this::toCaseItem).toList(),
            recentActivity()
        );
    }

    private UUID resolveUserId(String username) {
        return userRepository.findByUsernameAndDeletedFalse(username)
            .orElseThrow(() -> new ResourceNotFoundException("User", username))
            .getId();
    }

    private DashboardCaseItemResponse toCaseItem(Case c) {
        return new DashboardCaseItemResponse(
            c.getId(),
            c.getCaseNumber(),
            c.getTitle(),
            c.getStatus().getCode(),
            c.getStatus().getDisplayName(),
            c.getPriority().getCode(),
            c.getPriority().getDisplayName(),
            c.getDueDate(),
            c.getAssignedToId(),
            c.getUpdatedAt()
        );
    }

    private List<DashboardActivityResponse> recentActivity() {
        List<CaseNote> notes = caseNoteRepository.findByDeletedFalseOrderByCreatedAtDesc(PageRequest.of(0, 8));
        List<CaseTask> tasks = caseTaskRepository.findByDeletedFalseOrderByUpdatedAtDesc(PageRequest.of(0, 8));
        List<CaseStatusHistory> statusChanges = statusHistoryRepository.findAllByOrderByChangedAtDesc(PageRequest.of(0, 8));

        List<UUID> caseIds = new ArrayList<>();
        notes.forEach(n -> caseIds.add(n.getCaseId()));
        tasks.forEach(t -> caseIds.add(t.getCaseId()));
        statusChanges.forEach(s -> caseIds.add(s.getCaseId()));

        Map<UUID, Case> casesById = caseRepository.findAllById(caseIds).stream()
            .filter(c -> !c.isDeleted())
            .collect(Collectors.toMap(Case::getId, Function.identity()));

        List<DashboardActivityResponse> activity = new ArrayList<>();
        for (CaseNote note : notes) {
            Case c = casesById.get(note.getCaseId());
            if (c != null) {
                activity.add(new DashboardActivityResponse(
                    "NOTE",
                    c.getId(),
                    c.getCaseNumber(),
                    c.getTitle(),
                    truncate(note.getBody(), 100),
                    note.getCreatedBy(),
                    note.getCreatedAt()
                ));
            }
        }

        for (CaseTask task : tasks) {
            Case c = casesById.get(task.getCaseId());
            if (c != null) {
                activity.add(new DashboardActivityResponse(
                    "TASK",
                    c.getId(),
                    c.getCaseNumber(),
                    c.getTitle(),
                    task.getTitle(),
                    task.getUpdatedBy() == null ? task.getCreatedBy() : task.getUpdatedBy(),
                    task.getUpdatedAt()
                ));
            }
        }

        for (CaseStatusHistory status : statusChanges) {
            Case c = casesById.get(status.getCaseId());
            if (c != null) {
                activity.add(new DashboardActivityResponse(
                    "STATUS",
                    c.getId(),
                    c.getCaseNumber(),
                    c.getTitle(),
                    "Status changed to " + c.getStatus().getDisplayName(),
                    status.getChangedBy(),
                    status.getChangedAt()
                ));
            }
        }

        return activity.stream()
            .sorted(Comparator.comparing(
                DashboardActivityResponse::occurredAt,
                Comparator.nullsLast(Instant::compareTo)
            ).reversed())
            .limit(10)
            .toList();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }
}
