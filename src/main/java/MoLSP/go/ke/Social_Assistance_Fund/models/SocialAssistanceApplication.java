package MoLSP.go.ke.Social_Assistance_Fund.models;

import MoLSP.go.ke.Social_Assistance_Fund.enums.ApplicationStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "social_assistance_applications")
@Getter
@Setter
public class SocialAssistanceApplication extends Base {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "applicant_id", nullable = false)
    private Applicant applicant;

    @Column(name = "application_date")
    private LocalDate applicationDate;

    @Column(name = "applicant_signed_date")
    private LocalDate applicantSignedDate;

    @Column(name = "official_name", length = 200)
    private String officialName;

    @Column(name = "official_designation", length = 200)
    private String officialDesignation;

    @Column(name = "official_signature_reference", length = 200)
    private String officialSignatureReference;

    @Column(name = "official_signed_date")
    private LocalDate officialSignedDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_code", nullable = false, length = 30)
    private ApplicationStatus status;

    @Column(name = "decision_reason", length = 1000)
    private String decisionReason;

    @Column(name = "other_programme", length = 200)
    private String otherProgramme;

    @Column(nullable = false, length = 20)
    private String source;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "application_programmes", joinColumns = @JoinColumn(name = "application_id"))
    @Column(name = "programme_code", nullable = false, length = 250)
    private Set<String> programmeCodes = new HashSet<>();
}
