package MoLSP.go.ke.Social_Assistance_Fund.dtos;

import MoLSP.go.ke.Social_Assistance_Fund.enums.ApplicationStatus;
import MoLSP.go.ke.Social_Assistance_Fund.enums.Gender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ApplicationDtos {
    private ApplicationDtos() {
    }

    public record FormRequest(
            @Size(max = 200) String firstName,
            @Size(max = 200) String middleName,
            @Size(max = 200) String lastName,
            Gender gender,
            LocalDate dateOfBirth,
            @Min(0) @Max(120) Integer age,
            @Size(max = 30) String maritalStatus,
            @Pattern(regexp = "[A-Za-z0-9]{1,20}") String identityNumber,
            @Min(1) Integer subCountyId,
            @Size(max = 150) String location,
            @Size(max = 150) String subLocation,
            @Size(max = 150) String village,
            @Size(max = 500) String postalAddress,
            @Size(max = 500) String physicalAddress,
            List<@NotBlank @Size(max = 64) String> telephoneContacts,
            LocalDate applicationDate,
            List<@NotBlank @Size(max = 40) String> programmeCodes,
            @Size(max = 200) String otherProgramme,
            LocalDate applicantSignedDate) {
    }

    public record UpdateRequest(@NotNull Long version, @NotNull @Valid FormRequest form) {
    }

    public record ActionRequest(@NotNull Long version) {
    }

    public record DecisionRequest(@NotNull Long version, @NotBlank @Size(max = 1000) String reason) {
    }

    public record ApprovalRequest(
            @NotNull Long version,
            @NotBlank @Size(max = 200) String officialName,
            @NotBlank @Size(max = 200) String officialDesignation,
            @NotBlank @Size(max = 200) String officialSignatureReference,
            @NotNull LocalDate officialSignedDate) {
    }

    public record ApplicationResponse(
            UUID id,
            ApplicationStatus status,
            String source,
            String firstName,
            String middleName,
            String lastName,
            Gender gender,
            LocalDate dateOfBirth,
            Integer age,
            String maritalStatus,
            String identityNumber,
            Integer countyId,
            Integer subCountyId,
            String location,
            String subLocation,
            String village,
            String postalAddress,
            String physicalAddress,
            List<String> telephoneContacts,
            LocalDate applicationDate,
            List<String> programmeCodes,
            String otherProgramme,
            LocalDate applicantSignedDate,
            String officialName,
            String officialDesignation,
            String officialSignatureReference,
            LocalDate officialSignedDate,
            String decisionReason,
            long version,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record RegistrationReceipt(UUID applicationId, ApplicationStatus status) {
    }
}
