package com.caseaxis.reports;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportsService {

    private final NamedParameterJdbcTemplate jdbc;

    public ReportSummaryResponse summary(ReportFilters filters) {
        MapSqlParameterSource params = params(filters);
        Long totalCases = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM cases c
            JOIN case_statuses s ON s.id = c.status_id
            JOIN case_types t ON t.id = c.type_id
            WHERE c.is_deleted = false
            """ + caseFilters("c", true, filters), params, Long.class);
        Long openCases = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM cases c
            JOIN case_statuses s ON s.id = c.status_id
            JOIN case_types t ON t.id = c.type_id
            WHERE c.is_deleted = false AND s.is_terminal = false
            """ + caseFilters("c", true, filters), params, Long.class);
        Long closedCases = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM cases c
            JOIN case_statuses s ON s.id = c.status_id
            JOIN case_types t ON t.id = c.type_id
            WHERE c.is_deleted = false
              AND (c.closed_at IS NOT NULL OR c.resolved_at IS NOT NULL)
            """ + caseFilters("c", false, filters) + closurePeriodFilter("c", filters), params, Long.class);
        Long overdueCases = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM cases c
            JOIN case_statuses s ON s.id = c.status_id
            JOIN case_types t ON t.id = c.type_id
            WHERE c.is_deleted = false
              AND s.is_terminal = false
              AND c.due_date < CURRENT_DATE
            """ + caseFilters("c", true, filters), params, Long.class);
        Long escalatedCases = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM cases c
            JOIN case_statuses s ON s.id = c.status_id
            JOIN case_types t ON t.id = c.type_id
            WHERE c.is_deleted = false AND s.code = 'ESCALATED'
            """ + caseFilters("c", true, filters), params, Long.class);
        Double avgResolutionHours = jdbc.queryForObject("""
            SELECT AVG(EXTRACT(EPOCH FROM (COALESCE(c.closed_at, c.resolved_at) - c.created_at)) / 3600.0)
            FROM cases c
            JOIN case_statuses s ON s.id = c.status_id
            JOIN case_types t ON t.id = c.type_id
            WHERE c.is_deleted = false
              AND (c.closed_at IS NOT NULL OR c.resolved_at IS NOT NULL)
            """ + caseFilters("c", false, filters) + closurePeriodFilter("c", filters), params, Double.class);
        Long openTasks = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM case_tasks task
            JOIN task_statuses ts ON ts.id = task.status_id
            JOIN cases c ON c.id = task.case_id
            JOIN case_statuses s ON s.id = c.status_id
            JOIN case_types t ON t.id = c.type_id
            WHERE task.is_deleted = false AND c.is_deleted = false AND ts.is_terminal = false
            """ + caseFilters("c", true, filters, false) + taskAssigneeFilter(filters), params, Long.class);
        Long completedTasks = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM case_tasks task
            JOIN task_statuses ts ON ts.id = task.status_id
            JOIN cases c ON c.id = task.case_id
            JOIN case_statuses s ON s.id = c.status_id
            JOIN case_types t ON t.id = c.type_id
            WHERE task.is_deleted = false AND c.is_deleted = false AND ts.code = 'COMPLETED'
            """ + caseFilters("c", false, filters, false) + taskCompletedPeriodFilter(filters) + taskAssigneeFilter(filters), params, Long.class);

        return new ReportSummaryResponse(
            zero(totalCases),
            zero(openCases),
            zero(closedCases),
            zero(overdueCases),
            zero(escalatedCases),
            avgResolutionHours,
            zero(openTasks),
            zero(completedTasks)
        );
    }

    public List<DistributionItemResponse> statusDistribution(ReportFilters filters) {
        return jdbc.query("""
            SELECT s.code, s.display_name, COUNT(c.id) AS count_value
            FROM cases c
            JOIN case_statuses s ON s.id = c.status_id
            JOIN case_types t ON t.id = c.type_id
            WHERE c.is_deleted = false
            """ + caseFilters("c", true, filters) + """
            GROUP BY s.code, s.display_name, s.sort_order
            ORDER BY s.sort_order
            """, params(filters), (rs, rowNum) -> new DistributionItemResponse(
                rs.getString("code"),
                rs.getString("display_name"),
                rs.getLong("count_value")
            ));
    }

    public List<DistributionItemResponse> typeDistribution(ReportFilters filters) {
        return jdbc.query("""
            SELECT t.code, t.display_name, COUNT(c.id) AS count_value
            FROM cases c
            JOIN case_statuses s ON s.id = c.status_id
            JOIN case_types t ON t.id = c.type_id
            WHERE c.is_deleted = false
            """ + caseFilters("c", true, filters) + """
            GROUP BY t.code, t.display_name, t.sort_order
            ORDER BY t.sort_order
            """, params(filters), (rs, rowNum) -> new DistributionItemResponse(
                rs.getString("code"),
                rs.getString("display_name"),
                rs.getLong("count_value")
            ));
    }

    public List<OverdueAgingBucketResponse> overdueAging(ReportFilters filters) {
        Map<String, OverdueAgingBucketResponse> buckets = new LinkedHashMap<>();
        buckets.put("1-7 days", new OverdueAgingBucketResponse("1-7 days", 1, 7, 0));
        buckets.put("8-30 days", new OverdueAgingBucketResponse("8-30 days", 8, 30, 0));
        buckets.put("31-90 days", new OverdueAgingBucketResponse("31-90 days", 31, 90, 0));
        buckets.put("90+ days", new OverdueAgingBucketResponse("90+ days", 91, null, 0));

        jdbc.query("""
            SELECT
              CASE
                WHEN CURRENT_DATE - c.due_date BETWEEN 1 AND 7 THEN '1-7 days'
                WHEN CURRENT_DATE - c.due_date BETWEEN 8 AND 30 THEN '8-30 days'
                WHEN CURRENT_DATE - c.due_date BETWEEN 31 AND 90 THEN '31-90 days'
                ELSE '90+ days'
              END AS bucket,
              COUNT(*) AS count_value
            FROM cases c
            JOIN case_statuses s ON s.id = c.status_id
            JOIN case_types t ON t.id = c.type_id
            WHERE c.is_deleted = false
              AND s.is_terminal = false
              AND c.due_date < CURRENT_DATE
            """ + caseFilters("c", true, filters) + """
            GROUP BY bucket
            """, params(filters), rs -> {
            String bucket = rs.getString("bucket");
            OverdueAgingBucketResponse current = buckets.get(bucket);
            if (current != null) {
                buckets.put(bucket, new OverdueAgingBucketResponse(
                    current.bucket(), current.minDays(), current.maxDays(), rs.getLong("count_value")));
            }
        });
        return List.copyOf(buckets.values());
    }

    public List<AssigneeWorkloadResponse> assigneeWorkload(ReportFilters filters, String sort) {
        String orderBy = switch (sort == null ? "" : sort) {
            case "overdueCases" -> "overdue_cases DESC, assignee_name ASC";
            case "escalatedCases" -> "escalated_cases DESC, assignee_name ASC";
            case "closedThisPeriod" -> "closed_this_period DESC, assignee_name ASC";
            default -> "open_cases DESC, assignee_name ASC";
        };
        return jdbc.query("""
            SELECT
              u.id AS assignee_id,
              COALESCE(NULLIF(TRIM(CONCAT(u.first_name, ' ', u.last_name)), ''), u.username, 'Unassigned') AS assignee_name,
              COUNT(*) FILTER (WHERE s.is_terminal = false) AS open_cases,
              COUNT(*) FILTER (WHERE s.is_terminal = false AND c.due_date < CURRENT_DATE) AS overdue_cases,
              COUNT(*) FILTER (WHERE s.code = 'ESCALATED') AS escalated_cases,
              COUNT(*) FILTER (
                WHERE (c.closed_at IS NOT NULL OR c.resolved_at IS NOT NULL)
            """ + closurePeriodFilter("c", filters) + """
              ) AS closed_this_period
            FROM cases c
            JOIN case_statuses s ON s.id = c.status_id
            JOIN case_types t ON t.id = c.type_id
            LEFT JOIN users u ON u.id = c.assigned_to_id
            WHERE c.is_deleted = false
            """ + caseFilters("c", true, filters) + """
            GROUP BY u.id, u.first_name, u.last_name, u.username
            """ + " ORDER BY " + orderBy + " LIMIT 50 " + """
            """, params(filters), (rs, rowNum) -> new AssigneeWorkloadResponse(
                rs.getObject("assignee_id", java.util.UUID.class),
                rs.getString("assignee_name"),
                rs.getLong("open_cases"),
                rs.getLong("overdue_cases"),
                rs.getLong("escalated_cases"),
                rs.getLong("closed_this_period")
            ));
    }

    public List<OrganizationWorkloadResponse> organizationWorkload(ReportFilters filters, String sort) {
        String orderBy = switch (sort == null ? "" : sort) {
            case "openCases" -> "open_cases DESC, organization_name ASC";
            case "escalatedCases" -> "escalated_cases DESC, organization_name ASC";
            default -> "total_cases DESC, organization_name ASC";
        };
        return jdbc.query("""
            SELECT
              o.id AS organization_id,
              o.organization_code,
              o.name AS organization_name,
              COUNT(c.id) AS total_cases,
              COUNT(c.id) FILTER (WHERE s.is_terminal = false) AS open_cases,
              COUNT(c.id) FILTER (WHERE s.code = 'ESCALATED') AS escalated_cases
            FROM cases c
            JOIN case_statuses s ON s.id = c.status_id
            JOIN case_types t ON t.id = c.type_id
            LEFT JOIN organizations o ON o.id = c.organization_id
            WHERE c.is_deleted = false
            """ + caseFilters("c", true, filters) + """
            GROUP BY o.id, o.organization_code, o.name
            """ + " ORDER BY " + orderBy + " LIMIT 50 " + """
            """, params(filters), (rs, rowNum) -> new OrganizationWorkloadResponse(
                rs.getObject("organization_id", java.util.UUID.class),
                rs.getString("organization_code"),
                rs.getString("organization_name") == null ? "Unassigned organization" : rs.getString("organization_name"),
                rs.getLong("total_cases"),
                rs.getLong("open_cases"),
                rs.getLong("escalated_cases")
            ));
    }

    public List<ClosureTrendPointResponse> closureTrend(ReportFilters filters) {
        LocalDate start = filters.startDate() == null ? LocalDate.now().minusDays(29) : filters.startDate();
        LocalDate end = filters.endDate() == null ? LocalDate.now() : filters.endDate();
        ReportFilters effectiveFilters = new ReportFilters(
            start,
            end,
            filters.organizationId(),
            filters.clientId(),
            filters.caseType(),
            filters.status(),
            filters.assigneeId()
        );
        Map<LocalDate, Long> counts = new LinkedHashMap<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            counts.put(day, 0L);
        }

        jdbc.query("""
            SELECT CAST(COALESCE(c.closed_at, c.resolved_at) AT TIME ZONE 'UTC' AS DATE) AS closed_date,
                   COUNT(*) AS count_value
            FROM cases c
            JOIN case_statuses s ON s.id = c.status_id
            JOIN case_types t ON t.id = c.type_id
            WHERE c.is_deleted = false
              AND (c.closed_at IS NOT NULL OR c.resolved_at IS NOT NULL)
            """ + caseFilters("c", false, effectiveFilters) + closurePeriodFilter("c", effectiveFilters) + """
            GROUP BY closed_date
            ORDER BY closed_date
            """, params(effectiveFilters), (org.springframework.jdbc.core.RowCallbackHandler) rs -> counts.put(
                rs.getObject("closed_date", LocalDate.class),
                rs.getLong("count_value")
            ));

        return counts.entrySet().stream()
            .map(entry -> new ClosureTrendPointResponse(entry.getKey(), entry.getValue()))
            .toList();
    }

    public ReportExportResponse exportJson(ReportFilters filters) {
        return new ReportExportResponse(
            summary(filters),
            statusDistribution(filters),
            typeDistribution(filters),
            overdueAging(filters),
            assigneeWorkload(filters, null),
            organizationWorkload(filters, null),
            closureTrend(filters)
        );
    }

    public String exportCsv(ReportFilters filters) {
        StringBuilder csv = new StringBuilder();
        ReportSummaryResponse summary = summary(filters);
        csv.append("section,metric,value\n");
        csv.append("summary,totalCases,").append(summary.totalCases()).append('\n');
        csv.append("summary,openCases,").append(summary.openCases()).append('\n');
        csv.append("summary,closedCases,").append(summary.closedCases()).append('\n');
        csv.append("summary,overdueCases,").append(summary.overdueCases()).append('\n');
        csv.append("summary,escalatedCases,").append(summary.escalatedCases()).append('\n');
        csv.append("summary,averageResolutionHours,").append(summary.averageResolutionHours() == null ? "" : summary.averageResolutionHours()).append('\n');
        csv.append("summary,openTasks,").append(summary.openTasks()).append('\n');
        csv.append("summary,completedTasks,").append(summary.completedTasks()).append('\n');

        csv.append("\nsection,code,label,count\n");
        appendDistribution(csv, "statusDistribution", statusDistribution(filters));
        appendDistribution(csv, "typeDistribution", typeDistribution(filters));

        csv.append("\nsection,bucket,minDays,maxDays,count\n");
        for (OverdueAgingBucketResponse row : overdueAging(filters)) {
            csv.append("overdueAging,").append(escape(row.bucket())).append(',')
                .append(row.minDays()).append(',')
                .append(row.maxDays() == null ? "" : row.maxDays()).append(',')
                .append(row.count()).append('\n');
        }

        csv.append("\nsection,assignee,openCases,overdueCases,escalatedCases,closedThisPeriod\n");
        for (AssigneeWorkloadResponse row : assigneeWorkload(filters, null)) {
            csv.append("assigneeWorkload,").append(escape(row.assigneeName())).append(',')
                .append(row.openCases()).append(',')
                .append(row.overdueCases()).append(',')
                .append(row.escalatedCases()).append(',')
                .append(row.closedThisPeriod()).append('\n');
        }

        csv.append("\nsection,organization,totalCases,openCases,escalatedCases\n");
        for (OrganizationWorkloadResponse row : organizationWorkload(filters, null)) {
            csv.append("organizationWorkload,").append(escape(row.organizationName())).append(',')
                .append(row.totalCases()).append(',')
                .append(row.openCases()).append(',')
                .append(row.escalatedCases()).append('\n');
        }

        csv.append("\nsection,date,count\n");
        for (ClosureTrendPointResponse row : closureTrend(filters)) {
            csv.append("closureTrend,").append(row.date()).append(',').append(row.count()).append('\n');
        }
        return csv.toString();
    }

    private void appendDistribution(StringBuilder csv, String section, List<DistributionItemResponse> rows) {
        for (DistributionItemResponse row : rows) {
            csv.append(section).append(',')
                .append(escape(row.code())).append(',')
                .append(escape(row.label())).append(',')
                .append(row.count()).append('\n');
        }
    }

    private MapSqlParameterSource params(ReportFilters filters) {
        LocalDate start = filters.startDate();
        LocalDate end = filters.endDate();
        return new MapSqlParameterSource()
            .addValue("startInstant", start == null ? null : start.atStartOfDay().atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
            .addValue("endInstantExclusive", end == null ? null : end.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
            .addValue("organizationId", filters.organizationId(), Types.OTHER)
            .addValue("clientId", filters.clientId(), Types.OTHER)
            .addValue("caseType", filters.caseType(), Types.VARCHAR)
            .addValue("status", filters.status(), Types.VARCHAR)
            .addValue("assigneeId", filters.assigneeId(), Types.OTHER)
            .addValue("startDate", start == null ? null : Date.valueOf(start), Types.DATE)
            .addValue("endDateExclusive", end == null ? null : Date.valueOf(end.plusDays(1)), Types.DATE);
    }

    private String caseFilters(String caseAlias, boolean includeCreatedPeriod, ReportFilters filters) {
        return caseFilters(caseAlias, includeCreatedPeriod, filters, true);
    }

    private String caseFilters(String caseAlias, boolean includeCreatedPeriod, ReportFilters filters, boolean includeAssignee) {
        StringBuilder sql = new StringBuilder();
        if (includeCreatedPeriod && filters.startDate() != null) {
            sql.append(" AND ").append(caseAlias).append(".created_at >= :startInstant");
        }
        if (includeCreatedPeriod && filters.endDate() != null) {
            sql.append(" AND ").append(caseAlias).append(".created_at < :endInstantExclusive");
        }
        if (filters.organizationId() != null) {
            sql.append(" AND ").append(caseAlias).append(".organization_id = :organizationId");
        }
        if (filters.clientId() != null) {
            sql.append(" AND ").append(caseAlias).append(".client_id = :clientId");
        }
        if (filters.caseType() != null) {
            sql.append(" AND t.code = :caseType");
        }
        if (filters.status() != null) {
            sql.append(" AND s.code = :status");
        }
        if (includeAssignee && filters.assigneeId() != null) {
            sql.append(" AND ").append(caseAlias).append(".assigned_to_id = :assigneeId");
        }
        return sql.append('\n').toString();
    }

    private String closurePeriodFilter(String caseAlias, ReportFilters filters) {
        StringBuilder sql = new StringBuilder();
        if (filters.startDate() != null) {
            sql.append(" AND COALESCE(").append(caseAlias).append(".closed_at, ")
                .append(caseAlias).append(".resolved_at) >= :startInstant");
        }
        if (filters.endDate() != null) {
            sql.append(" AND COALESCE(").append(caseAlias).append(".closed_at, ")
                .append(caseAlias).append(".resolved_at) < :endInstantExclusive");
        }
        return sql.append('\n').toString();
    }

    private String taskCompletedPeriodFilter(ReportFilters filters) {
        StringBuilder sql = new StringBuilder();
        if (filters.startDate() != null) {
            sql.append(" AND task.completed_at >= :startInstant");
        }
        if (filters.endDate() != null) {
            sql.append(" AND task.completed_at < :endInstantExclusive");
        }
        return sql.append('\n').toString();
    }

    private String taskAssigneeFilter(ReportFilters filters) {
        return filters.assigneeId() == null ? "" : " AND task.assigned_to_id = :assigneeId";
    }

    private long zero(Long value) {
        return value == null ? 0 : value;
    }

    private String escape(String value) {
        if (value == null) return "";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
