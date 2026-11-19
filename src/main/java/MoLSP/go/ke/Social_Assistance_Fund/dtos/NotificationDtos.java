package MoLSP.go.ke.Social_Assistance_Fund.dtos;

import MoLSP.go.ke.Social_Assistance_Fund.enums.NotificationChannel;
import MoLSP.go.ke.Social_Assistance_Fund.enums.NotificationEventType;
import MoLSP.go.ke.Social_Assistance_Fund.enums.NotificationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class NotificationDtos {
    private NotificationDtos() {
    }

    public record DeliveryAttemptResponse(
            int attemptNo,
            Instant startedAt,
            Instant finishedAt,
            String outcome,
            String errorCategory,
            String providerReference) {
    }

    public record NotificationResponse(
            UUID id,
            UUID auditEventId,
            NotificationEventType eventType,
            NotificationChannel channel,
            String recipient,
            String subject,
            String body,
            NotificationStatus status,
            int attempts,
            int maxAttempts,
            Instant nextAttemptAt,
            String lastError,
            String providerReference,
            String requestId,
            Instant createdAt,
            Instant updatedAt,
            Instant sentAt,
            List<DeliveryAttemptResponse> deliveryAttempts) {
    }
}
