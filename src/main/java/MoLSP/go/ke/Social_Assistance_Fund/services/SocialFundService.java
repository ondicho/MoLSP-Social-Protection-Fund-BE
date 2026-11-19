package MoLSP.go.ke.Social_Assistance_Fund.services;

import MoLSP.go.ke.Social_Assistance_Fund.dtos.PaginatedResponse;
import MoLSP.go.ke.Social_Assistance_Fund.dtos.ResponseWrapper;
import MoLSP.go.ke.Social_Assistance_Fund.dtos.SocialFundDtos;
import MoLSP.go.ke.Social_Assistance_Fund.enums.FundCoverage;
import MoLSP.go.ke.Social_Assistance_Fund.enums.FundType;
import MoLSP.go.ke.Social_Assistance_Fund.exceptions.DuplicateResourceException;
import MoLSP.go.ke.Social_Assistance_Fund.exceptions.ResourceNotFoundException;
import MoLSP.go.ke.Social_Assistance_Fund.models.SocialFund;
import MoLSP.go.ke.Social_Assistance_Fund.repositories.SocialFundRepository;
import MoLSP.go.ke.Social_Assistance_Fund.utils.PaginationUtil;
import MoLSP.go.ke.Social_Assistance_Fund.utils.ResponseHandler;
import MoLSP.go.ke.Social_Assistance_Fund.utils.StandardUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.EntityManager;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SocialFundService {
    private final SocialFundRepository funds;
    private final JdbcTemplate jdbc;
    private final PaginationUtil pagination;
    private final ResponseHandler responses;
    private final StandardUtil standardUtil;
    private final org.springframework.data.domain.AuditorAware<String> auditor;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public ResponseEntity<ResponseWrapper<PaginatedResponse<SocialFundDtos.FundResponse>>> list(
            Integer pageNo, Integer pageSize, String sortBy, String sortDirection, String search,
            FundType type, String requestId) {
        validateSort(sortBy);
        Pageable pageable = pagination.buildPageable(pageNo, pageSize, sortBy, sortDirection, "createdAt");
        Page<SocialFundDtos.FundResponse> page = funds.findAll(specification(search, type), pageable)
                .map(this::toResponse);
        return responses.paginated(page, "Funds retrieved", requestId);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<ResponseWrapper<SocialFundDtos.FundResponse>> get(UUID id, String requestId) {
        return responses.success(toResponse(find(id)), "Fund retrieved", requestId);
    }

    @Transactional
    public ResponseEntity<ResponseWrapper<SocialFundDtos.FundResponse>> create(
            SocialFundDtos.CreateRequest request, String requestId) {
        validate(request.type(), request.openDate(), request.closeDate(),
                request.minAmount(), request.maxAmount(), request.countyIds());
        SocialFund fund = new SocialFund();
        apply(fund, request.name(), request.type(), request.coverage(), request.openDate(),
                request.closeDate(), request.minAmount(), request.maxAmount(), request.countyIds());
        fund.setSlug(uniqueSlug(request.name(), request.openDate(), request.closeDate(), null));
        return responses.created(toResponse(funds.save(fund)), "Fund created", requestId);
    }

    @Transactional
    public ResponseEntity<ResponseWrapper<SocialFundDtos.FundResponse>> update(
            UUID id, SocialFundDtos.UpdateRequest request, String requestId) {
        SocialFund fund = find(id);
        if (fund.getVersion() != request.version()) {
            throw new ObjectOptimisticLockingFailureException(SocialFund.class, id);
        }
        validate(request.type(), request.openDate(), request.closeDate(),
                request.minAmount(), request.maxAmount(), request.countyIds());
        apply(fund, request.name(), request.type(), request.coverage(), request.openDate(),
                request.closeDate(), request.minAmount(), request.maxAmount(), request.countyIds());
        fund.setSlug(uniqueSlug(request.name(), request.openDate(), request.closeDate(), id));
        funds.save(fund);
        entityManager.flush();
        return responses.success(toResponse(fund), "Fund updated", requestId);
    }

    @Transactional
    public ResponseEntity<ResponseWrapper<SocialFundDtos.ArchiveResponse>> toggleArchive(UUID id, String requestId) {
        String actor = auditor.getCurrentAuditor().orElseThrow();
        Boolean archived = jdbc.query("SELECT deleted_at IS NOT NULL FROM social_funds WHERE id = ?",
                rs -> rs.next() ? rs.getBoolean(1) : null, uuidBytes(id));
        if (archived == null) {
            throw new ResourceNotFoundException("Fund", id);
        }
        int changed;
        if (archived) {
            changed = jdbc.update("""
                    UPDATE social_funds SET deleted_at = NULL, deleted_by = NULL,
                        updated_at = ?, updated_by = ?, version = version + 1
                    WHERE id = ? AND deleted_at IS NOT NULL
                    """, Timestamp.from(Instant.now()), actor, uuidBytes(id));
        } else {
            changed = jdbc.update("""
                    UPDATE social_funds SET deleted_at = ?, deleted_by = ?,
                        updated_at = ?, updated_by = ?, version = version + 1
                    WHERE id = ? AND deleted_at IS NULL
                    """, Timestamp.from(Instant.now()), actor, Timestamp.from(Instant.now()), actor, uuidBytes(id));
        }
        if (changed != 1) {
            throw new ObjectOptimisticLockingFailureException(SocialFund.class, id);
        }
        return responses.success(new SocialFundDtos.ArchiveResponse(id, !archived),
                archived ? "Fund unarchived" : "Fund archived", requestId);
    }

    private Specification<SocialFund> specification(String search, FundType type) {
        Specification<SocialFund> spec = (root, query, cb) -> cb.conjunction();
        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.strip().toLowerCase(java.util.Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("slug")), pattern)));
        }
        if (type != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("type"), type));
        }
        return spec;
    }

    private void validate(FundType type, java.time.LocalDate openDate, java.time.LocalDate closeDate,
                          BigDecimal minAmount, BigDecimal maxAmount, List<Integer> countyIds) {
        if (openDate == null || closeDate == null || !openDate.isBefore(closeDate)) {
            throw new IllegalArgumentException("openDate must be before closeDate");
        }
        if (minAmount == null || maxAmount == null || minAmount.signum() < 0
                || maxAmount.signum() < 0 || minAmount.compareTo(maxAmount) > 0) {
            throw new IllegalArgumentException("Amounts must be non-negative and minAmount must not exceed maxAmount");
        }
        Set<Integer> ids = countyIds == null ? Set.of() : Set.copyOf(countyIds);
        if (countyIds != null && countyIds.size() != ids.size()) {
            throw new IllegalArgumentException("countyIds must not contain duplicates");
        }
        if (type == FundType.NATIONAL && !ids.isEmpty()) {
            throw new IllegalArgumentException("National funds cannot be restricted to counties");
        }
        if (type == FundType.COUNTY && ids.isEmpty()) {
            throw new IllegalArgumentException("County funds require at least one county");
        }
        if (!ids.isEmpty()) {
            Integer found = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM counties WHERE id IN (" + ids.stream().map(id -> "?").collect(Collectors.joining(",")) + ")",
                    Integer.class, ids.toArray());
            if (found == null || found != ids.size()) {
                throw new ResourceNotFoundException("One or more counties");
            }
        }
    }

    private void apply(SocialFund fund, String name, FundType type,
                       FundCoverage coverage,
                       java.time.LocalDate openDate, java.time.LocalDate closeDate,
                       BigDecimal minAmount, BigDecimal maxAmount, List<Integer> countyIds) {
        fund.setName(name.strip());
        fund.setType(type);
        fund.setCoverage(coverage);
        fund.setOpenDate(openDate);
        fund.setCloseDate(closeDate);
        fund.setMinAmount(minAmount);
        fund.setMaxAmount(maxAmount);
        fund.setCountyIds(countyIds == null ? new HashSet<>() : new HashSet<>(countyIds));
    }

    private String uniqueSlug(String name, java.time.LocalDate openDate, java.time.LocalDate closeDate, UUID id) {
        String base = standardUtil.generateSlug(name + "-" + openDate + "-" + closeDate);
        if (base.isBlank()) {
            throw new IllegalArgumentException("Fund name must produce a valid slug");
        }
        String candidate = base;
        int suffix = 2;
        while (slugExists(candidate, id)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private boolean slugExists(String slug, UUID id) {
        Integer count = id == null
                ? jdbc.queryForObject("SELECT COUNT(*) FROM social_funds WHERE slug = ?", Integer.class, slug)
                : jdbc.queryForObject("SELECT COUNT(*) FROM social_funds WHERE slug = ? AND id <> ?",
                        Integer.class, slug, uuidBytes(id));
        return count != null && count > 0;
    }

    private void validateSort(String sortBy) {
        if (sortBy != null && !sortBy.isBlank()
                && !java.util.Set.of("createdAt", "updatedAt", "name", "slug", "type", "openDate", "closeDate")
                .contains(sortBy)) {
            throw new IllegalArgumentException("Unsupported fund sort field");
        }
    }

    private SocialFund find(UUID id) {
        return funds.findById(id).orElseThrow(() -> new ResourceNotFoundException("Fund", id));
    }

    private SocialFundDtos.FundResponse toResponse(SocialFund fund) {
        return new SocialFundDtos.FundResponse(fund.getId(), fund.getName(), fund.getSlug(), fund.getType(),
                fund.getCoverage(), fund.getOpenDate(), fund.getCloseDate(), fund.getMinAmount(),
                fund.getMaxAmount(), fund.getCountyIds().stream().sorted().toList(),
                fund.getVersion(), fund.getCreatedAt(), fund.getUpdatedAt());
    }

    private byte[] uuidBytes(UUID id) {
        return ByteBuffer.allocate(16)
                .putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits())
                .array();
    }
}
