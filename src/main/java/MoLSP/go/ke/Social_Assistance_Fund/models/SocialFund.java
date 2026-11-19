package MoLSP.go.ke.Social_Assistance_Fund.models;

import MoLSP.go.ke.Social_Assistance_Fund.enums.FundCoverage;
import MoLSP.go.ke.Social_Assistance_Fund.enums.FundType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "social_funds", uniqueConstraints = @UniqueConstraint(name = "uk_social_funds_slug", columnNames = "slug"))
@Getter
@Setter
public class SocialFund extends Base {
    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 250)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FundType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "fund_coverage", nullable = false, length = 20)
    private FundCoverage coverage;

    @Column(name = "open_date", nullable = false)
    private LocalDate openDate;

    @Column(name = "close_date", nullable = false)
    private LocalDate closeDate;

    @Column(name = "min_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal minAmount;

    @Column(name = "max_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal maxAmount;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "social_fund_counties", joinColumns = @JoinColumn(name = "fund_id"))
    @Column(name = "county_id", nullable = false)
    private Set<Integer> countyIds = new HashSet<>();
}
