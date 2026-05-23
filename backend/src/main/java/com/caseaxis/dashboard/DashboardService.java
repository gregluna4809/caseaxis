package com.caseaxis.dashboard;

import com.caseaxis.cases.CaseRepository;
import com.caseaxis.common.exception.ResourceNotFoundException;
import com.caseaxis.users.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final Clock clock = Clock.systemUTC();

    @Transactional(readOnly = true)
    public DashboardMetricsResponse getMetrics(String username) {
        UUID userId = userRepository.findByUsernameAndDeletedFalse(username)
            .orElseThrow(() -> new ResourceNotFoundException("User", username))
            .getId();

        LocalDate today = LocalDate.now(clock);
        var startOfDay = today.atStartOfDay().toInstant(ZoneOffset.UTC);
        var startOfTomorrow = today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        return new DashboardMetricsResponse(
            caseRepository.countActive(),
            caseRepository.countOpen(),
            caseRepository.countAssignedTo(userId),
            caseRepository.countOverdue(today),
            caseRepository.countEscalated(),
            caseRepository.countClosedBetween(startOfDay, startOfTomorrow)
        );
    }
}
