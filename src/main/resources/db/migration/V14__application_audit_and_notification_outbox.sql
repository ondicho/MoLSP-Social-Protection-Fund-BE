-- Insert-only audit log. The application never issues UPDATE/DELETE against this table; production
-- deployments should additionally restrict the application DB user to SELECT, INSERT on it.
CREATE TABLE application_audit_events (
    id BINARY(16) NOT NULL PRIMARY KEY,
    sequence_no BIGINT NOT NULL AUTO_INCREMENT,
    application_id BINARY(16) NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    actor_id VARCHAR(36) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    from_status VARCHAR(30) NULL,
    to_status VARCHAR(30) NULL,
    reason VARCHAR(1000) NULL,
    changes JSON NULL,
    request_id VARCHAR(100) NULL,
    CONSTRAINT fk_audit_events_application
        FOREIGN KEY (application_id) REFERENCES social_assistance_applications (id),
    CONSTRAINT fk_audit_events_from_status FOREIGN KEY (from_status) REFERENCES application_statuses (code),
    CONSTRAINT fk_audit_events_to_status FOREIGN KEY (to_status) REFERENCES application_statuses (code),
    CONSTRAINT chk_audit_events_type CHECK (event_type IN ('CREATED', 'UPDATED', 'SUBMITTED', 'RESUBMITTED',
        'REVIEW_STARTED', 'APPROVED', 'REJECTED', 'RETURNED_FOR_CORRECTION')),
    CONSTRAINT uk_audit_events_sequence UNIQUE (sequence_no),
    INDEX idx_audit_events_application_time (application_id, occurred_at)
);

INSERT INTO application_audit_events
    (id, application_id, event_type, actor_id, occurred_at, from_status, to_status, reason, changes, request_id)
SELECT id, application_id,
       CASE
           WHEN previous_status IS NULL THEN 'CREATED'
           WHEN previous_status = new_status THEN 'UPDATED'
           WHEN new_status = 'SUBMITTED' AND previous_status = 'RETURNED_FOR_CORRECTION' THEN 'RESUBMITTED'
           WHEN new_status = 'SUBMITTED' THEN 'SUBMITTED'
           WHEN new_status = 'UNDER_REVIEW' THEN 'REVIEW_STARTED'
           ELSE new_status
       END,
       actor_id, occurred_at, previous_status, new_status, reason, NULL, NULL
FROM application_transitions
ORDER BY occurred_at, created_at;

DROP TABLE application_transitions;

CREATE TABLE notification_outbox (
    id BINARY(16) NOT NULL PRIMARY KEY,
    application_id BINARY(16) NOT NULL,
    audit_event_id BINARY(16) NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    channel VARCHAR(10) NOT NULL,
    recipient VARCHAR(254) NOT NULL,
    subject VARCHAR(200) NOT NULL,
    title VARCHAR(100) NOT NULL,
    body VARCHAR(2000) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL,
    next_attempt_at TIMESTAMP(6) NOT NULL,
    locked_until TIMESTAMP(6) NULL,
    claim_token VARCHAR(36) NULL,
    last_error VARCHAR(100) NULL,
    provider_reference VARCHAR(200) NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    request_id VARCHAR(100) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    sent_at TIMESTAMP(6) NULL,
    CONSTRAINT uk_notification_outbox_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_notification_outbox_application
        FOREIGN KEY (application_id) REFERENCES social_assistance_applications (id),
    CONSTRAINT fk_notification_outbox_audit_event
        FOREIGN KEY (audit_event_id) REFERENCES application_audit_events (id),
    CONSTRAINT chk_notification_outbox_event CHECK (event_type IN
        ('APPLICATION_RECEIVED', 'RETURNED_FOR_CORRECTION', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_notification_outbox_channel CHECK (channel IN ('SMS', 'EMAIL')),
    CONSTRAINT chk_notification_outbox_status CHECK (status IN
        ('PENDING', 'SENDING', 'RETRY_SCHEDULED', 'SENT', 'FAILED')),
    CONSTRAINT chk_notification_outbox_attempts CHECK (attempts >= 0 AND max_attempts > 0),
    INDEX idx_notification_outbox_due (status, next_attempt_at),
    INDEX idx_notification_outbox_application (application_id, created_at)
);

CREATE TABLE notification_delivery_attempts (
    id BINARY(16) NOT NULL PRIMARY KEY,
    notification_id BINARY(16) NOT NULL,
    attempt_no INT NOT NULL,
    started_at TIMESTAMP(6) NOT NULL,
    finished_at TIMESTAMP(6) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    error_category VARCHAR(100) NULL,
    provider_reference VARCHAR(200) NULL,
    CONSTRAINT fk_delivery_attempts_notification
        FOREIGN KEY (notification_id) REFERENCES notification_outbox (id),
    CONSTRAINT uk_delivery_attempts_number UNIQUE (notification_id, attempt_no),
    CONSTRAINT chk_delivery_attempts_outcome CHECK (outcome IN ('SENT', 'RETRYABLE_FAILURE', 'PERMANENT_FAILURE'))
);
