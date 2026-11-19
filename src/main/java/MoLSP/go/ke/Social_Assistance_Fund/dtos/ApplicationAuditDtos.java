package MoLSP.go.ke.Social_Assistance_Fund.dtos;

import MoLSP.go.ke.Social_Assistance_Fund.enums.ApplicationStatus;
import MoLSP.go.ke.Social_Assistance_Fund.enums.AuditEventType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ApplicationAuditDtos {
    private ApplicationAuditDtos() {
    }

    public record FieldChange(String field, Object before, Object after) {
    }

    public record AuditEventResponse(
            UUID id,
            long sequenceNo,
            AuditEventType eventType,
            String actorId,
            String actorName,
            String actorRole,
            Instant occurredAt,
            ApplicationStatus fromStatus,
            ApplicationStatus toStatus,
            String reason,
            List<FieldChange> changes,
            String requestId) {
    }

    public record AvailableAction(
            String action,
            String method,
            String path,
            ApplicationStatus nextStatus,
            List<String> requiredFields) {
    }

    public record AvailableActionsResponse(
            UUID applicationId,
            ApplicationStatus status,
            long version,
            List<AvailableAction> actions) {
    }
}
