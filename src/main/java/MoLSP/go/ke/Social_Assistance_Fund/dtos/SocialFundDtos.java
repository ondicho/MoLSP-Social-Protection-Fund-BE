package MoLSP.go.ke.Social_Assistance_Fund.dtos;

import MoLSP.go.ke.Social_Assistance_Fund.enums.FundCoverage;
import MoLSP.go.ke.Social_Assistance_Fund.enums.FundType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class SocialFundDtos {
    private SocialFundDtos() {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 200) String name,
            @NotNull FundType type,
            @NotNull FundCoverage coverage,
            @NotNull LocalDate openDate,
            @NotNull LocalDate closeDate,
            @NotNull @DecimalMin(value = "0.00", inclusive = true) BigDecimal minAmount,
            @NotNull @DecimalMin(value = "0.00", inclusive = true) BigDecimal maxAmount,
            @Size(max = 47) List<@NotNull @Positive Integer> countyIds) {
    }

    public record UpdateRequest(
            @NotBlank @Size(max = 200) String name,
            @NotNull FundType type,
            @NotNull FundCoverage coverage,
            @NotNull LocalDate openDate,
            @NotNull LocalDate closeDate,
            @NotNull @DecimalMin(value = "0.00", inclusive = true) BigDecimal minAmount,
            @NotNull @DecimalMin(value = "0.00", inclusive = true) BigDecimal maxAmount,
            @Size(max = 47) List<@NotNull @Positive Integer> countyIds,
            @NotNull Long version) {
    }

    public record FundResponse(
            UUID id,
            String name,
            String slug,
            FundType type,
            FundCoverage coverage,
            LocalDate openDate,
            LocalDate closeDate,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            List<Integer> countyIds,
            long version,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record ArchiveResponse(UUID id, boolean archived) {
    }
}
