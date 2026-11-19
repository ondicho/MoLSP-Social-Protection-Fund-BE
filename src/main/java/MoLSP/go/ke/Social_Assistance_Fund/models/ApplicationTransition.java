package MoLSP.go.ke.Social_Assistance_Fund.models;

import MoLSP.go.ke.Social_Assistance_Fund.enums.ApplicationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "application_transitions")
@Getter
@Setter
public class ApplicationTransition extends Base {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private SocialAssistanceApplication application;

    @Column(name = "actor_id", nullable = false, length = 36)
    private String actorId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 30)
    private ApplicationStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 30)
    private ApplicationStatus newStatus;

    @Column(length = 1000)
    private String reason;
}
