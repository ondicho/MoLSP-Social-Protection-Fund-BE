package MoLSP.go.ke.Social_Assistance_Fund.services;

import MoLSP.go.ke.Social_Assistance_Fund.dtos.NotificationDtos;
import MoLSP.go.ke.Social_Assistance_Fund.enums.NotificationChannel;
import MoLSP.go.ke.Social_Assistance_Fund.enums.NotificationEventType;
import MoLSP.go.ke.Social_Assistance_Fund.enums.NotificationStatus;
import MoLSP.go.ke.Social_Assistance_Fund.models.SocialAssistanceApplication;
import MoLSP.go.ke.Social_Assistance_Fund.notifications.DeliveryResult;
import MoLSP.go.ke.Social_Assistance_Fund.notifications.NotificationGateway;
import MoLSP.go.ke.Social_Assistance_Fund.notifications.NotificationProperties;
import MoLSP.go.ke.Social_Assistance_Fund.notifications.OutboundNotification;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static MoLSP.go.ke.Social_Assistance_Fund.utils.UuidBytes.fromBytes;
import static MoLSP.go.ke.Social_Assistance_Fund.utils.UuidBytes.toBytes;

/**
 * Single owner of applicant communications: message composition, transactional outbox enqueue,
 * asynchronous post-commit dispatch, scheduled retry and lifecycle queries.
 */
@Service
@Slf4j
public class NotificationService {
    public static final String PROVIDER_NOT_CONFIGURED = "PROVIDER_NOT_CONFIGURED";
    private static final int MAX_BODY = 2000;
    private final JdbcTemplate jdbc;
    private final ObjectProvider<NotificationGateway> gateways;
    private final NotificationProperties properties;
    private final ApplicationEventPublisher events;

    public NotificationService(JdbcTemplate jdbc, ObjectProvider<NotificationGateway> gateways,
                               NotificationProperties properties, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.gateways = gateways;
        this.properties = properties;
        this.events = events;
    }

    public record NotificationEnqueued(UUID notificationId) {
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reportProviderStatus() {
        NotificationGateway gateway = gateways.getIfAvailable();
        Long pending = jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE status IN (?, ?, ?)",
                Long.class, NotificationStatus.PENDING.name(), NotificationStatus.RETRY_SCHEDULED.name(),
                NotificationStatus.SENDING.name());
        if (gateway == null) {
            log.warn("event=notification_provider_unavailable configuredProvider={} queuedNotifications={} "
                            + "action=notifications_will_remain_in_outbox_until_a_provider_is_configured",
                    properties.provider(), pending);
        } else {
            log.info("event=notification_provider_ready provider={} queuedNotifications={}",
                    gateway.providerName(), pending);
        }
    }

    /** Enqueues inside the caller's transaction so the notification commits or rolls back with the workflow. */
    public void applicationReceived(SocialAssistanceApplication application, UUID auditEventId, String requestId) {
        String funds = fundNames(application);
        enqueue(application, auditEventId, NotificationEventType.APPLICATION_RECEIVED, "Application received",
                "Dear %s, your application to %s has been received. The %s will review it and respond on your "
                        .formatted(firstName(application), funds, properties.ministryName())
                        + "eligibility. Ref: " + reference(application) + ".", requestId);
    }

    public void returnedForCorrection(SocialAssistanceApplication application, String reason, UUID auditEventId,
                                      String requestId) {
        enqueue(application, auditEventId, NotificationEventType.RETURNED_FOR_CORRECTION,
                "Application returned for correction",
                "Dear %s, your application to %s has been returned for correction. Reason: %s. Please contact "
                        .formatted(firstName(application), fundNames(application), shorten(reason, 300))
                        + "your sub-county social protection office. Ref: " + reference(application) + ".",
                requestId);
    }

    public void decision(SocialAssistanceApplication application, boolean approved, String reason,
                         UUID auditEventId, String requestId) {
        String funds = fundNames(application);
        String body = approved
                ? "Dear %s, your application to %s has been approved. The %s will contact you on the next steps. "
                .formatted(firstName(application), funds, properties.ministryName())
                : "Dear %s, your application to %s has been declined. Reason: %s. "
                .formatted(firstName(application), funds, shorten(reason, 300));
        enqueue(application, auditEventId, approved ? NotificationEventType.APPROVED : NotificationEventType.REJECTED,
                approved ? "Application approved" : "Application declined",
                body + "Ref: " + reference(application) + ".", requestId);
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationEnqueued(NotificationEnqueued event) {
        dispatch(event.notificationId());
    }

    @Scheduled(fixedDelayString = "${app.notifications.poll-interval:PT30S}",
            initialDelayString = "${app.notifications.poll-initial-delay:PT20S}")
    public void pollOutbox() {
        try {
            dispatchDue();
        } catch (RuntimeException exception) {
            log.error("event=notification_poll_failed error={}", exception.getClass().getSimpleName());
        }
    }

    /** Drains due and lease-expired notifications. Returns the number of dispatch attempts made. */
    public int dispatchDue() {
        if (gateways.getIfAvailable() == null) {
            return 0;
        }
        Timestamp now = Timestamp.from(Instant.now());
        int exhausted = jdbc.update("""
                UPDATE notification_outbox
                SET status = 'FAILED', last_error = 'LEASE_EXPIRED_AFTER_FINAL_ATTEMPT', claim_token = NULL,
                    locked_until = NULL, updated_at = ?
                WHERE status = 'SENDING' AND locked_until < ? AND attempts >= max_attempts
                """, now, now);
        if (exhausted > 0) {
            log.warn("event=notification_lease_expired_final count={}", exhausted);
        }
        List<UUID> due = jdbc.query("""
                SELECT id FROM notification_outbox
                WHERE ((status IN ('PENDING', 'RETRY_SCHEDULED') AND next_attempt_at <= ?)
                       OR (status = 'SENDING' AND locked_until < ?))
                  AND attempts < max_attempts
                ORDER BY next_attempt_at
                LIMIT ?
                """, (rs, rowNum) -> fromBytes(rs.getBytes(1)), now, now, properties.batchSize());
        int attempted = 0;
        for (UUID id : due) {
            if (dispatch(id)) {
                attempted++;
            }
        }
        return attempted;
    }

    /**
     * Claims one notification with a conditional UPDATE (only one worker can win), sends it, and records the
     * outcome. Returns false when the row was not claimable or no provider is available.
     */
    public boolean dispatch(UUID notificationId) {
        NotificationGateway gateway = gateways.getIfAvailable();
        if (gateway == null) {
            return false;
        }
        Instant started = Instant.now();
        String claim = UUID.randomUUID().toString();
        int claimed = jdbc.update("""
                UPDATE notification_outbox
                SET status = 'SENDING', claim_token = ?, locked_until = ?, attempts = attempts + 1, updated_at = ?
                WHERE id = ? AND attempts < max_attempts
                  AND ((status IN ('PENDING', 'RETRY_SCHEDULED') AND next_attempt_at <= ?)
                       OR (status = 'SENDING' AND locked_until < ?))
                """, claim, Timestamp.from(started.plus(properties.lease())), Timestamp.from(started),
                toBytes(notificationId), Timestamp.from(started), Timestamp.from(started));
        if (claimed != 1) {
            return false;
        }
        Claimed row = jdbc.queryForObject("""
                SELECT channel, recipient, subject, title, body, attempts, max_attempts, request_id
                FROM notification_outbox WHERE id = ?
                """, (rs, rowNum) -> new Claimed(new OutboundNotification(notificationId,
                NotificationChannel.valueOf(rs.getString("channel")), rs.getString("recipient"),
                rs.getString("subject"), rs.getString("title"), rs.getString("body")),
                rs.getInt("attempts"), rs.getInt("max_attempts"), rs.getString("request_id")), toBytes(notificationId));
        String previousRequestId = MDC.get("requestId");
        if (row.requestId() != null) {
            MDC.put("requestId", row.requestId());
        }
        try {
            DeliveryResult result;
            try {
                result = gateway.send(row.message());
                if (result == null) {
                    result = DeliveryResult.retryable("PROVIDER_EMPTY_RESULT");
                }
            } catch (RuntimeException exception) {
                result = DeliveryResult.retryable("PROVIDER_ERROR");
            }
            complete(notificationId, claim, row, result, started, gateway.providerName());
            return true;
        } finally {
            if (previousRequestId == null) MDC.remove("requestId"); else MDC.put("requestId", previousRequestId);
        }
    }

    private void complete(UUID id, String claim, Claimed row, DeliveryResult result, Instant started,
                          String provider) {
        Instant finished = Instant.now();
        String outcome;
        int updated;
        if (result.delivered()) {
            outcome = "SENT";
            updated = jdbc.update("""
                    UPDATE notification_outbox
                    SET status = 'SENT', sent_at = ?, provider_reference = ?, last_error = NULL,
                        claim_token = NULL, locked_until = NULL, updated_at = ?
                    WHERE id = ? AND claim_token = ?
                    """, Timestamp.from(finished), result.providerReference(), Timestamp.from(finished),
                    toBytes(id), claim);
        } else if (result.retryable() && row.attempts() < row.maxAttempts()) {
            outcome = "RETRYABLE_FAILURE";
            updated = jdbc.update("""
                    UPDATE notification_outbox
                    SET status = 'RETRY_SCHEDULED', next_attempt_at = ?, last_error = ?,
                        claim_token = NULL, locked_until = NULL, updated_at = ?
                    WHERE id = ? AND claim_token = ?
                    """, Timestamp.from(finished.plus(properties.backoffForAttempt(row.attempts()))),
                    result.errorCategory(), Timestamp.from(finished), toBytes(id), claim);
        } else {
            outcome = result.retryable() ? "RETRYABLE_FAILURE" : "PERMANENT_FAILURE";
            updated = jdbc.update("""
                    UPDATE notification_outbox
                    SET status = 'FAILED', last_error = ?, claim_token = NULL, locked_until = NULL, updated_at = ?
                    WHERE id = ? AND claim_token = ?
                    """, result.errorCategory(), Timestamp.from(finished), toBytes(id), claim);
        }
        jdbc.update("""
                INSERT INTO notification_delivery_attempts
                    (id, notification_id, attempt_no, started_at, finished_at, outcome, error_category,
                     provider_reference)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, toBytes(UUID.randomUUID()), toBytes(id), row.attempts(), Timestamp.from(started),
                Timestamp.from(finished), outcome, result.errorCategory(), result.providerReference());
        log.info("event=notification_dispatch_completed notificationId={} provider={} attempt={} maxAttempts={} "
                        + "outcome={} errorCategory={} leaseRetained={} durationMs={}", id, provider, row.attempts(),
                row.maxAttempts(), outcome, result.errorCategory(), updated == 1,
                java.time.Duration.between(started, finished).toMillis());
    }

    public Page<NotificationDtos.NotificationResponse> forApplication(UUID applicationId, Pageable pageable) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE application_id = ?",
                Long.class, toBytes(applicationId));
        List<NotificationDtos.NotificationResponse> rows = jdbc.query("""
                        SELECT id, audit_event_id, event_type, channel, recipient, subject, body, status, attempts,
                               max_attempts, next_attempt_at, last_error, provider_reference, request_id,
                               created_at, updated_at, sent_at
                        FROM notification_outbox WHERE application_id = ?
                        ORDER BY created_at %s, id
                        LIMIT ? OFFSET ?
                        """.formatted(pageable.getSort().iterator().next().isAscending() ? "ASC" : "DESC"),
                (rs, rowNum) -> new NotificationDtos.NotificationResponse(fromBytes(rs.getBytes("id")),
                        fromBytes(rs.getBytes("audit_event_id")),
                        NotificationEventType.valueOf(rs.getString("event_type")),
                        NotificationChannel.valueOf(rs.getString("channel")), maskRecipient(rs.getString("recipient")),
                        rs.getString("subject"), rs.getString("body"),
                        NotificationStatus.valueOf(rs.getString("status")), rs.getInt("attempts"),
                        rs.getInt("max_attempts"), instant(rs.getTimestamp("next_attempt_at")),
                        rs.getString("last_error"), rs.getString("provider_reference"), rs.getString("request_id"),
                        instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("updated_at")),
                        instant(rs.getTimestamp("sent_at")), List.of()),
                toBytes(applicationId), pageable.getPageSize(), pageable.getOffset());
        Map<UUID, List<NotificationDtos.DeliveryAttemptResponse>> attempts = attempts(rows);
        List<NotificationDtos.NotificationResponse> content = rows.stream().map(row ->
                new NotificationDtos.NotificationResponse(row.id(), row.auditEventId(), row.eventType(), row.channel(),
                        row.recipient(), row.subject(), row.body(), row.status(), row.attempts(), row.maxAttempts(),
                        row.nextAttemptAt(), row.lastError(), row.providerReference(), row.requestId(),
                        row.createdAt(), row.updatedAt(), row.sentAt(),
                        attempts.getOrDefault(row.id(), List.of()))).toList();
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private Map<UUID, List<NotificationDtos.DeliveryAttemptResponse>> attempts(
            List<NotificationDtos.NotificationResponse> notifications) {
        if (notifications.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", Collections.nCopies(notifications.size(), "?"));
        Map<UUID, List<NotificationDtos.DeliveryAttemptResponse>> result = new java.util.HashMap<>();
        jdbc.query("""
                SELECT notification_id, attempt_no, started_at, finished_at, outcome, error_category,
                       provider_reference
                FROM notification_delivery_attempts WHERE notification_id IN (%s)
                ORDER BY attempt_no
                """.formatted(placeholders), rs -> {
            result.computeIfAbsent(fromBytes(rs.getBytes("notification_id")), key -> new ArrayList<>())
                    .add(new NotificationDtos.DeliveryAttemptResponse(rs.getInt("attempt_no"),
                            instant(rs.getTimestamp("started_at")), instant(rs.getTimestamp("finished_at")),
                            rs.getString("outcome"), rs.getString("error_category"),
                            rs.getString("provider_reference")));
        }, notifications.stream().map(row -> (Object) toBytes(row.id())).toArray());
        return result;
    }

    private void enqueue(SocialAssistanceApplication application, UUID auditEventId,
                         NotificationEventType eventType, String subject, String body, String requestId) {
        String recipient = recipient(application);
        if (recipient == null) {
            log.warn("event=notification_skipped reason=NO_RECIPIENT applicationId={} notificationEvent={}",
                    application.getId(), eventType);
            return;
        }
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        boolean providerAvailable = gateways.getIfAvailable() != null;
        jdbc.update("""
                        INSERT INTO notification_outbox
                            (id, application_id, audit_event_id, event_type, channel, recipient, subject, title,
                             body, status, attempts, max_attempts, next_attempt_at, last_error, idempotency_key,
                             request_id, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, ?, ?, ?, ?, ?, ?, ?)
                        """, toBytes(id), toBytes(application.getId()), toBytes(auditEventId), eventType.name(),
                NotificationChannel.SMS.name(), recipient, subject, properties.messageTitle(), shorten(body, MAX_BODY),
                properties.maxAttempts(), now, providerAvailable ? null : PROVIDER_NOT_CONFIGURED,
                eventType.name() + ":" + auditEventId, requestId, now, now);
        log.info("event=notification_enqueued notificationId={} applicationId={} notificationEvent={} channel=SMS "
                + "providerAvailable={}", id, application.getId(), eventType, providerAvailable);
        events.publishEvent(new NotificationEnqueued(id));
    }

    private String recipient(SocialAssistanceApplication application) {
        return application.getApplicant().getTelephoneContacts().stream().sorted().findFirst()
                .map(phone -> phone.startsWith("+") ? phone.substring(1) : phone).orElse(null);
    }

    private String fundNames(SocialAssistanceApplication application) {
        List<String> slugs = application.getProgrammeCodes().stream().sorted().toList();
        if (slugs.isEmpty()) {
            return "the Social Assistance Fund";
        }
        String placeholders = String.join(",", Collections.nCopies(slugs.size(), "?"));
        List<String> names = jdbc.queryForList("SELECT name FROM social_funds WHERE slug IN (" + placeholders
                + ") ORDER BY name", String.class, slugs.toArray());
        return names.isEmpty() ? "the Social Assistance Fund" : names.stream().collect(Collectors.joining(", "));
    }

    private String firstName(SocialAssistanceApplication application) {
        String name = application.getApplicant().getFirstName();
        return name == null || name.isBlank() ? "Applicant" : name;
    }

    private String reference(SocialAssistanceApplication application) {
        return application.getId().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
    }

    private static String shorten(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }

    static String maskRecipient(String recipient) {
        if (recipient == null || recipient.length() <= 4) {
            return "****";
        }
        if (recipient.contains("@")) {
            return recipient.charAt(0) + "***" + recipient.substring(recipient.indexOf('@'));
        }
        return "*".repeat(recipient.length() - 4) + recipient.substring(recipient.length() - 4);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record Claimed(OutboundNotification message, int attempts, int maxAttempts, String requestId) {
    }
}
