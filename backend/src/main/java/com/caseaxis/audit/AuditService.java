package com.caseaxis.audit;

import com.caseaxis.common.util.UuidGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public static Map<String, Object> fields(Object... keyValues) {
        if (keyValues == null || keyValues.length == 0) {
            return Map.of();
        }
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Audit fields require key/value pairs");
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            values.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return values;
    }

    public void recordCaseEvent(
            UUID actorId,
            UUID caseId,
            String action,
            Map<String, ?> oldValues,
            Map<String, ?> newValues,
            Map<String, ?> metadata) {
        record(actorId, "case", caseId, action, oldValues, newValues, metadata);
    }

    public void recordEntityEvent(
            UUID actorId,
            String entityType,
            UUID entityId,
            String action,
            Map<String, ?> oldValues,
            Map<String, ?> newValues,
            Map<String, ?> metadata) {
        record(actorId, entityType, entityId, action, oldValues, newValues, metadata);
    }

    @Transactional(readOnly = true)
    public List<AuditEventResponse> getCaseAuditEvents(UUID caseId) {
        return jdbc.query("""
            SELECT al.id,
                   al.occurred_at,
                   al.actor_id,
                   COALESCE(NULLIF(TRIM(CONCAT(u.first_name, ' ', u.last_name)), ''), u.username, 'System') AS actor_display_name,
                   al.action,
                   al.old_values,
                   al.new_values,
                   al.metadata
            FROM audit_logs al
            LEFT JOIN users u ON u.id = al.actor_id
            WHERE al.entity_type = 'case'
              AND al.entity_id = :caseId
            ORDER BY al.occurred_at DESC
            LIMIT 100
            """, new MapSqlParameterSource("caseId", caseId), (rs, rowNum) -> {
            String action = rs.getString("action");
            JsonNode oldValues = readJson(rs.getString("old_values"));
            JsonNode newValues = readJson(rs.getString("new_values"));
            JsonNode metadata = readJson(rs.getString("metadata"));
            return new AuditEventResponse(
                rs.getObject("id", UUID.class),
                rs.getTimestamp("occurred_at").toInstant(),
                rs.getObject("actor_id", UUID.class),
                rs.getString("actor_display_name"),
                action,
                eventType(action),
                summary(action, oldValues, newValues, metadata)
            );
        });
    }

    private void record(
            UUID actorId,
            String entityType,
            UUID entityId,
            String action,
            Map<String, ?> oldValues,
            Map<String, ?> newValues,
            Map<String, ?> metadata) {
        jdbc.update("""
            INSERT INTO audit_logs (id, occurred_at, actor_id, entity_type, entity_id, action,
                                    old_values, new_values, metadata)
            VALUES (:id, :occurredAt, :actorId, :entityType, :entityId, :action,
                    CAST(:oldValues AS jsonb), CAST(:newValues AS jsonb), CAST(:metadata AS jsonb))
            """, new MapSqlParameterSource()
            .addValue("id", UuidGenerator.generate(), Types.OTHER)
            .addValue("occurredAt", OffsetDateTime.now(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
            .addValue("actorId", actorId, Types.OTHER)
            .addValue("entityType", entityType, Types.VARCHAR)
            .addValue("entityId", entityId, Types.OTHER)
            .addValue("action", action, Types.VARCHAR)
            .addValue("oldValues", toJson(oldValues), Types.VARCHAR)
            .addValue("newValues", toJson(newValues), Types.VARCHAR)
            .addValue("metadata", toJson(metadata), Types.VARCHAR));
    }

    private String toJson(Map<String, ?> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize audit payload", ex);
        }
    }

    private JsonNode readJson(String value) {
        if (value == null || value.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException ex) {
            return objectMapper.createObjectNode();
        }
    }

    private String eventType(String action) {
        return switch (action) {
            case AuditAction.CASE_CREATED -> "Case created";
            case AuditAction.CASE_ASSIGNED -> "Case assigned";
            case AuditAction.CASE_REASSIGNED -> "Case reassigned";
            case AuditAction.CASE_STATUS_CHANGED -> "Status changed";
            case AuditAction.CASE_ARCHIVED -> "Case archived";
            case AuditAction.CASE_REOPENED -> "Case reopened";
            case AuditAction.CASE_PRIORITY_CHANGED -> "Priority changed";
            case AuditAction.TASK_CREATED -> "Task created";
            case AuditAction.TASK_UPDATED -> "Task updated";
            case AuditAction.TASK_COMPLETED -> "Task completed";
            case AuditAction.TASK_DELETED -> "Task deleted";
            case AuditAction.NOTE_CREATED -> "Note created";
            case AuditAction.NOTE_DELETED -> "Note deleted";
            case AuditAction.ATTACHMENT_REGISTERED -> "Attachment registered";
            case AuditAction.ATTACHMENT_DELETED -> "Attachment deleted";
            case AuditAction.CLIENT_DEACTIVATED -> "Client deactivated";
            case AuditAction.ORGANIZATION_DEACTIVATED -> "Organization deactivated";
            default -> "Audit event";
        };
    }

    private String summary(String action, JsonNode oldValues, JsonNode newValues, JsonNode metadata) {
        return switch (action) {
            case AuditAction.CASE_CREATED ->
                "Created case " + text(newValues, "caseNumber");
            case AuditAction.CASE_ASSIGNED ->
                "Assigned case to user " + text(newValues, "assigneeId");
            case AuditAction.CASE_REASSIGNED ->
                "Reassigned case from user " + text(oldValues, "assigneeId") + " to user " + text(newValues, "assigneeId");
            case AuditAction.CASE_STATUS_CHANGED ->
                "Changed status from " + text(oldValues, "status") + " to " + text(newValues, "status");
            case AuditAction.CASE_ARCHIVED ->
                "Archived case as CLOSED";
            case AuditAction.CASE_REOPENED ->
                "Reopened case; reopened count is " + text(newValues, "reopenedCount");
            case AuditAction.CASE_PRIORITY_CHANGED ->
                "Changed priority from " + text(oldValues, "priority") + " to " + text(newValues, "priority");
            case AuditAction.TASK_CREATED ->
                "Created task \"" + text(metadata, "taskTitle") + "\"";
            case AuditAction.TASK_UPDATED ->
                "Updated task \"" + text(metadata, "taskTitle") + "\"";
            case AuditAction.TASK_COMPLETED ->
                "Completed task \"" + text(metadata, "taskTitle") + "\"";
            case AuditAction.TASK_DELETED ->
                "Deleted task \"" + text(metadata, "taskTitle") + "\"";
            case AuditAction.NOTE_CREATED ->
                "Added a note";
            case AuditAction.NOTE_DELETED ->
                "Deleted a note";
            case AuditAction.ATTACHMENT_REGISTERED ->
                "Registered attachment \"" + text(metadata, "filename") + "\"";
            case AuditAction.ATTACHMENT_DELETED ->
                "Deleted attachment \"" + text(metadata, "filename") + "\"";
            case AuditAction.CLIENT_DEACTIVATED ->
                "Deactivated client " + text(metadata, "clientNumber");
            case AuditAction.ORGANIZATION_DEACTIVATED ->
                "Deactivated organization " + text(metadata, "organizationCode");
            default -> "Recorded audit event";
        };
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        if (value == null || value.isNull()) {
            return "unknown";
        }
        return value.asText();
    }
}
