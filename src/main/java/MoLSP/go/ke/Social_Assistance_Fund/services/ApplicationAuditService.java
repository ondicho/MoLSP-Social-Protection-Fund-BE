package MoLSP.go.ke.Social_Assistance_Fund.services;

import MoLSP.go.ke.Social_Assistance_Fund.dtos.ApplicationAuditDtos;
import MoLSP.go.ke.Social_Assistance_Fund.enums.ApplicationStatus;
import MoLSP.go.ke.Social_Assistance_Fund.enums.AuditEventType;
import MoLSP.go.ke.Social_Assistance_Fund.utils.PublicRegistrationAuditContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static MoLSP.go.ke.Social_Assistance_Fund.utils.UuidBytes.fromBytes;
import static MoLSP.go.ke.Social_Assistance_Fund.utils.UuidBytes.toBytes;

/**
 * Append-only audit trail. This service deliberately exposes no update or delete operation.
 */
@Service
@RequiredArgsConstructor
public class ApplicationAuditService {
    private static final TypeReference<List<ApplicationAuditDtos.FieldChange>> CHANGES = new TypeReference<>() {
    };
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public UUID record(UUID applicationId, AuditEventType type, ApplicationStatus from, ApplicationStatus to,
                       String reason, List<ApplicationAuditDtos.FieldChange> changes, String actor,
                       String requestId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO application_audit_events
                            (id, application_id, event_type, actor_id, occurred_at, from_status, to_status,
                             reason, changes, request_id)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, toBytes(id), toBytes(applicationId), type.name(), actor, Timestamp.from(Instant.now()),
                from == null ? null : from.name(), to == null ? null : to.name(), reason,
                changes == null || changes.isEmpty() ? null : json.writeValueAsString(changes), requestId);
        return id;
    }

    public Page<ApplicationAuditDtos.AuditEventResponse> history(UUID applicationId, Pageable pageable) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM application_audit_events WHERE application_id = ?",
                Long.class, toBytes(applicationId));
        Sort.Order order = pageable.getSort().iterator().next();
        String direction = order.isAscending() ? "ASC" : "DESC";
        List<ApplicationAuditDtos.AuditEventResponse> content = jdbc.query("""
                        SELECT e.id, e.sequence_no, e.event_type, e.actor_id, e.occurred_at, e.from_status,
                               e.to_status, e.reason, e.changes, e.request_id, u.full_name, u.role_code
                        FROM application_audit_events e
                        LEFT JOIN users u
                            ON u.id = CASE WHEN IS_UUID(e.actor_id) THEN UUID_TO_BIN(e.actor_id) END
                        WHERE e.application_id = ?
                        ORDER BY e.sequence_no %s
                        LIMIT ? OFFSET ?
                        """.formatted(direction),
                (rs, rowNum) -> {
                    String actorId = rs.getString("actor_id");
                    boolean publicActor = PublicRegistrationAuditContext.ACTOR.equals(actorId);
                    String changes = rs.getString("changes");
                    return new ApplicationAuditDtos.AuditEventResponse(fromBytes(rs.getBytes("id")),
                            rs.getLong("sequence_no"), AuditEventType.valueOf(rs.getString("event_type")), actorId,
                            publicActor ? "Public self-registration" : rs.getString("full_name"),
                            publicActor ? "PUBLIC" : rs.getString("role_code"),
                            rs.getTimestamp("occurred_at").toInstant(), status(rs.getString("from_status")),
                            status(rs.getString("to_status")), rs.getString("reason"),
                            changes == null ? List.of() : json.readValue(changes, CHANGES),
                            rs.getString("request_id"));
                }, toBytes(applicationId), pageable.getPageSize(), pageable.getOffset());
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private ApplicationStatus status(String value) {
        return value == null ? null : ApplicationStatus.valueOf(value);
    }
}
